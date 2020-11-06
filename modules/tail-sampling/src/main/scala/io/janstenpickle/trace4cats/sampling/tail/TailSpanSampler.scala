package io.janstenpickle.trace4cats.sampling.tail

import cats.data.{NonEmptyList, NonEmptySet}
import cats.kernel.Semigroup
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.traverse._
import cats.{Applicative, Monad}
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, SampleDecision, TraceId}

trait TailSpanSampler[F[_]] {
  def sampleBatch(batch: Batch): F[Option[Batch]]
  def shouldSample(span: CompletedSpan): F[SampleDecision]
}

object TailSpanSampler {
  def head[F[_]: Applicative]: TailSpanSampler[F] =
    new TailSpanSampler[F] {
      override def shouldSample(span: CompletedSpan): F[SampleDecision] = span.context.traceFlags.sampled.pure[F]
      override def sampleBatch(batch: Batch): F[Option[Batch]] = {
        val spans = batch.spans.filter(_.context.traceFlags.sampled == SampleDecision.Include)

        (if (spans.isEmpty) None else Some(Batch(spans))).pure[F]
      }
    }

  private def storedShouldSample[F[_]: Monad](
    decider: CompletedSpan => F[SampleDecision],
    store: SampleDecisionStore[F]
  )(span: CompletedSpan): F[SampleDecision] = {
    val traceId = span.context.traceId
    store.getDecision(traceId).flatMap {
      case None =>
        decider(span).flatMap { decision =>
          store.storeDecision(traceId, decision).as(decision)
        }
      case Some(decision) => decision.pure[F]
    }
  }

  def storedIncrementalComputation[F[_]: Monad](
    store: SampleDecisionStore[F],
    decider: CompletedSpan => F[SampleDecision]
  ): TailSpanSampler[F] = {
    new TailSpanSampler[F] {
      override def shouldSample(span: CompletedSpan): F[SampleDecision] = storedShouldSample[F](decider, store)(span)

      override def sampleBatch(batch: Batch): F[Option[Batch]] =
        NonEmptyList.fromList(batch.spans.filter(_.context.traceFlags.sampled == SampleDecision.Include)).flatTraverse {
          spans =>
            store.batch(spans.map(_.context.traceId)).flatMap { decisions =>
              for {
                (sampledSpans, newDecisions) <-
                  spans
                    .foldM((List.empty[CompletedSpan], Map.empty[TraceId, SampleDecision])) {
                      case ((sampled, computedDecisions), span) =>
                        val traceId = span.context.traceId

                        decisions.get(traceId).orElse(computedDecisions.get(traceId)) match {
                          case None =>
                            decider(span).map { decision =>
                              val spans = if (decision == SampleDecision.Include) sampled else span :: sampled
                              spans -> computedDecisions.updated(traceId, decision)
                            }

                          case Some(SampleDecision.Drop) => Applicative[F].pure(sampled -> computedDecisions)
                          case Some(SampleDecision.Include) =>
                            Applicative[F].pure((span :: sampled) -> computedDecisions)
                        }

                    }

                _ <- store.storeDecisions(newDecisions)
              } yield if (sampledSpans.isEmpty) None else Some(Batch(sampledSpans))
            }
        }
    }
  }

  def storedBatchComputation[F[_]: Monad](
    store: SampleDecisionStore[F],
    decider: CompletedSpan => F[SampleDecision],
    batchDecider: List[CompletedSpan] => F[(List[CompletedSpan], Map[TraceId, SampleDecision])]
  ): TailSpanSampler[F] = {
    new TailSpanSampler[F] {
      override def shouldSample(span: CompletedSpan): F[SampleDecision] = storedShouldSample[F](decider, store)(span)

      override def sampleBatch(batch: Batch): F[Option[Batch]] =
        NonEmptyList.fromList(batch.spans.filter(_.context.traceFlags.sampled == SampleDecision.Include)).flatTraverse {
          spans =>
            for {
              decisions <- store.batch(spans.map(_.context.traceId))
              (missing, sampled) = spans.foldLeft((List.empty[CompletedSpan], List.empty[CompletedSpan])) {
                case ((missing, sampled), span) =>
                  decisions.get(span.context.traceId) match {
                    case None => (span :: missing) -> sampled
                    case Some(SampleDecision.Drop) => missing -> sampled
                    case Some(SampleDecision.Include) => missing -> (span :: sampled)
                  }
              }

              (computedSampled, computedDecisions) <- batchDecider(missing)
              _ <- store.storeDecisions(computedDecisions)
            } yield
              if (sampled.isEmpty && computedSampled.isEmpty) None
              else Some(Batch(sampled ++ computedSampled))
        }
    }
  }

  def filtering[F[_]: Monad](
    store: SampleDecisionStore[F],
    filter: CompletedSpan => SampleDecision
  ): TailSpanSampler[F] =
    storedIncrementalComputation[F](store, span => Applicative[F].pure(filter(span)))

  def spanNameFilter[F[_]: Monad](store: SampleDecisionStore[F], filter: String => SampleDecision): TailSpanSampler[F] =
    filtering(store, span => if (span.context.parent.isEmpty) filter(span.name) else SampleDecision.Include)

  def spanNameFilter[F[_]: Monad](store: SampleDecisionStore[F], contains: NonEmptySet[String]): TailSpanSampler[F] =
    spanNameFilter(store, name => SampleDecision(contains.exists(name.contains)))

  // Probabilistic sampler does not need a store, as decision is applied consistently based on trace ID
  def probabilistic[F[_]: Applicative](probability: Double): TailSpanSampler[F] = {
    val spanSampler: (TraceId, Option[SampleDecision]) => SampleDecision =
      SpanSampler.decideProbabilistic(probability, rootSpansOnly = false)

    new TailSpanSampler[F] {
      override def sampleBatch(batch: Batch): F[Option[Batch]] =
        Applicative[F].pure(
          NonEmptyList.fromList(batch.spans.filter(_.context.traceFlags.sampled == SampleDecision.Include)).flatMap {
            spans =>
              val (sampledSpans, _) =
                spans
                  .foldLeft((List.empty[CompletedSpan], Map.empty[TraceId, SampleDecision])) {
                    case ((sampled, computedDecisions), span) =>
                      val traceId = span.context.traceId

                      computedDecisions.get(traceId) match {
                        case None =>
                          spanSampler(span.context.traceId, None) match {
                            case SampleDecision.Drop =>
                              sampled -> computedDecisions.updated(span.context.traceId, SampleDecision.Drop)
                            case SampleDecision.Include =>
                              (span :: sampled) -> computedDecisions
                                .updated(span.context.traceId, SampleDecision.Include)
                          }

                        case Some(SampleDecision.Drop) => sampled -> computedDecisions
                        case Some(SampleDecision.Include) =>
                          (span :: sampled) -> computedDecisions
                      }

                  }

              if (sampledSpans.isEmpty) None else Some(batch.copy(spans = sampledSpans))
          }
        )

      override def shouldSample(span: CompletedSpan): F[SampleDecision] =
        Applicative[F].pure(spanSampler(span.context.traceId, None))
    }
  }

  def combined[F[_]: Monad](x: TailSpanSampler[F], y: TailSpanSampler[F]): TailSpanSampler[F] =
    new TailSpanSampler[F] {
      override def sampleBatch(batch: Batch): F[Option[Batch]] =
        x.sampleBatch(batch).flatMap(_.flatTraverse(y.sampleBatch))

      override def shouldSample(span: CompletedSpan): F[SampleDecision] =
        x.shouldSample(span).flatMap {
          case SampleDecision.Drop => Applicative[F].pure(SampleDecision.Drop)
          case SampleDecision.Include => y.shouldSample(span)
        }
    }

  implicit def semigroup[F[_]: Monad]: Semigroup[TailSpanSampler[F]] =
    new Semigroup[TailSpanSampler[F]] {
      override def combine(x: TailSpanSampler[F], y: TailSpanSampler[F]): TailSpanSampler[F] = combined(x, y)
    }
}
