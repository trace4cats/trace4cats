package io.janstenpickle.trace4cats.sampling.tail

import cats.data.NonEmptySet
import cats.kernel.Semigroup
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.functorFilter._
import cats.{Applicative, Foldable, FunctorFilter, Monad, MonoidK}
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, SampleDecision, TraceId}

trait TailSpanSampler[F[_], G[_]] {
  def sampleBatch(batch: Batch[G]): F[Batch[G]]
  def shouldSample(span: CompletedSpan): F[SampleDecision]
}

object TailSpanSampler {
  def head[F[_]: Applicative, G[_]: FunctorFilter]: TailSpanSampler[F, G] =
    new TailSpanSampler[F, G] {
      override def shouldSample(span: CompletedSpan): F[SampleDecision] = span.context.traceFlags.sampled.pure[F]
      override def sampleBatch(batch: Batch[G]): F[Batch[G]] = {
        val spans = batch.spans.filter(_.context.traceFlags.sampled == SampleDecision.Include)

        Batch(spans).pure[F]
      }
    }

  private def storedShouldSample[F[_]: Monad, G[_]](
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

  def combine[G[_]: Applicative: MonoidK](span: CompletedSpan, spans: G[CompletedSpan]): G[CompletedSpan] =
    MonoidK[G].combineK(spans, Applicative[G].pure(span))

  private def traceIds[G[_]: Foldable](spans: G[CompletedSpan]): Set[TraceId] =
    spans
      .foldLeft(Set.newBuilder[TraceId]) { (traceIds, span) =>
        if (span.context.traceFlags.sampled == SampleDecision.Include) traceIds += span.context.traceId
        else traceIds
      }
      .result()

  def storedIncrementalComputation[F[_]: Monad, G[_]: Applicative: Foldable: MonoidK](
    store: SampleDecisionStore[F],
    decider: CompletedSpan => F[SampleDecision]
  ): TailSpanSampler[F, G] = {
    new TailSpanSampler[F, G] {
      override def shouldSample(span: CompletedSpan): F[SampleDecision] = storedShouldSample[F, G](decider, store)(span)

      override def sampleBatch(batch: Batch[G]): F[Batch[G]] =
        store.batch(traceIds(batch.spans)).flatMap { decisions =>
          for {
            (sampledSpans, newDecisions) <-
              batch.spans
                .foldM((MonoidK[G].empty[CompletedSpan], Map.empty[TraceId, SampleDecision])) {
                  case (acc @ (sampled, computedDecisions), span) =>
                    if (span.context.traceFlags.sampled == SampleDecision.Drop) Applicative[F].pure(acc)
                    else {

                      val traceId = span.context.traceId

                      decisions.get(traceId).orElse(computedDecisions.get(traceId)) match {
                        case None =>
                          decider(span).map { decision =>
                            val spans = if (decision == SampleDecision.Include) sampled else combine(span, sampled)
                            spans -> computedDecisions.updated(traceId, decision)
                          }

                        case Some(SampleDecision.Drop) => Applicative[F].pure(sampled -> computedDecisions)
                        case Some(SampleDecision.Include) =>
                          Applicative[F].pure(combine(span, sampled) -> computedDecisions)
                      }

                    }
                }

            _ <- store.storeDecisions(newDecisions)
          } yield Batch(sampledSpans)
        }

    }
  }

  def storedBatchComputation[F[_]: Monad, G[_]: Applicative: Foldable: MonoidK](
    store: SampleDecisionStore[F],
    decider: CompletedSpan => F[SampleDecision],
    batchDecider: (G[CompletedSpan], Set[TraceId]) => F[(G[CompletedSpan], Map[TraceId, SampleDecision])]
  ): TailSpanSampler[F, G] = {
    new TailSpanSampler[F, G] {
      override def shouldSample(span: CompletedSpan): F[SampleDecision] = storedShouldSample[F, G](decider, store)(span)

      override def sampleBatch(batch: Batch[G]): F[Batch[G]] = {
        val batchTraces = traceIds(batch.spans)
        for {
          decisions <- store.batch(batchTraces)
          (missing, sampled) =
            batch.spans.foldLeft((MonoidK[G].empty[CompletedSpan], MonoidK[G].empty[CompletedSpan])) {
              case (acc @ (missing, sampled), span) =>
                if (span.context.traceFlags.sampled == SampleDecision.Drop) acc
                else
                  decisions.get(span.context.traceId) match {
                    case None => combine(span, missing) -> sampled
                    case Some(SampleDecision.Drop) => missing -> sampled
                    case Some(SampleDecision.Include) => missing -> combine(span, sampled)
                  }
            }
          (computedSampled, computedDecisions) <- batchDecider(missing, batchTraces)
          _ <- store.storeDecisions(computedDecisions)
        } yield Batch(MonoidK[G].combineK(sampled, computedSampled))
      }
    }
  }

  def filtering[F[_]: Monad, G[_]: Applicative: Foldable: FunctorFilter: MonoidK](
    store: SampleDecisionStore[F],
    filter: CompletedSpan => SampleDecision
  ): TailSpanSampler[F, G] =
    storedIncrementalComputation[F, G](store, span => Applicative[F].pure(filter(span)))

  def spanNameFilter[F[_]: Monad, G[_]: Applicative: Foldable: FunctorFilter: MonoidK](
    store: SampleDecisionStore[F],
    filter: String => SampleDecision
  ): TailSpanSampler[F, G] =
    filtering(store, span => if (span.context.parent.isEmpty) filter(span.name) else SampleDecision.Include)

  def spanNameFilter[F[_]: Monad, G[_]: Applicative: Foldable: FunctorFilter: MonoidK](
    store: SampleDecisionStore[F],
    contains: NonEmptySet[String]
  ): TailSpanSampler[F, G] =
    spanNameFilter(store, name => SampleDecision(contains.exists(name.contains)))

  // Probabilistic sampler does not need a store, as decision is applied consistently based on trace ID
  def probabilistic[F[_]: Applicative, G[_]: Applicative: Foldable: MonoidK](
    probability: Double
  ): TailSpanSampler[F, G] = {
    val spanSampler: (TraceId, Option[SampleDecision]) => SampleDecision =
      SpanSampler.decideProbabilistic(probability, rootSpansOnly = false)

    new TailSpanSampler[F, G] {
      override def sampleBatch(batch: Batch[G]): F[Batch[G]] = {

        val (sampledSpans, _) =
          batch.spans.foldLeft((MonoidK[G].empty[CompletedSpan], Map.empty[TraceId, SampleDecision])) {
            case (acc @ (sampled, computedDecisions), span) =>
              if (span.context.traceFlags.sampled == SampleDecision.Drop)
                acc
              else {
                val traceId = span.context.traceId

                computedDecisions.get(traceId) match {
                  case None =>
                    spanSampler(span.context.traceId, None) match {
                      case SampleDecision.Drop =>
                        sampled -> computedDecisions.updated(span.context.traceId, SampleDecision.Drop)
                      case SampleDecision.Include =>
                        combine(span, sampled) -> computedDecisions
                          .updated(span.context.traceId, SampleDecision.Include)
                    }

                  case Some(SampleDecision.Drop) => sampled -> computedDecisions
                  case Some(SampleDecision.Include) =>
                    combine(span, sampled) -> computedDecisions
                }

              }
          }

        Applicative[F].pure(Batch(sampledSpans))
      }

      override def shouldSample(span: CompletedSpan): F[SampleDecision] =
        Applicative[F].pure(spanSampler(span.context.traceId, None))
    }
  }

  def combined[F[_]: Monad, G[_]](x: TailSpanSampler[F, G], y: TailSpanSampler[F, G]): TailSpanSampler[F, G] =
    new TailSpanSampler[F, G] {
      override def sampleBatch(batch: Batch[G]): F[Batch[G]] =
        x.sampleBatch(batch).flatMap(y.sampleBatch)

      override def shouldSample(span: CompletedSpan): F[SampleDecision] =
        x.shouldSample(span).flatMap {
          case SampleDecision.Drop => Applicative[F].pure(SampleDecision.Drop)
          case SampleDecision.Include => y.shouldSample(span)
        }
    }

  implicit def semigroup[F[_]: Monad, G[_]]: Semigroup[TailSpanSampler[F, G]] =
    new Semigroup[TailSpanSampler[F, G]] {
      override def combine(x: TailSpanSampler[F, G], y: TailSpanSampler[F, G]): TailSpanSampler[F, G] = combined(x, y)
    }
}
