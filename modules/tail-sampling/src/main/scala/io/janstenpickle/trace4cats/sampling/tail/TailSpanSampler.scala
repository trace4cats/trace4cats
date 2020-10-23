package io.janstenpickle.trace4cats.sampling.tail

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.Sync
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceId}

trait TailSpanSampler[F[_]] {
  def sampleBatch(batch: Batch): F[Option[Batch]]
  def shouldSample(span: CompletedSpan): F[Boolean]
}

object TailSpanSampler {
  def head[F[_]: Applicative]: TailSpanSampler[F] = new TailSpanSampler[F] {
    override def shouldSample(span: CompletedSpan): F[Boolean] = span.context.traceFlags.sampled.pure[F]
    override def sampleBatch(batch: Batch): F[Option[Batch]] = {
      val spans = batch.spans.filterNot(_.context.traceFlags.sampled)

      (if (spans.isEmpty) None else Some(Batch(batch.process, spans))).pure[F]
    }
  }

  def probabilistic[F[_]: Sync](probability: Double, store: SampleDecisionStore[F]): TailSpanSampler[F] = {
    val spanSampler: (TraceId, Option[Boolean], Boolean) => Boolean = SpanSampler.decideProbabilistic(probability)

    new TailSpanSampler[F] {
      override def shouldSample(span: CompletedSpan): F[Boolean] = {
        val traceId = span.context.traceId
        store.getDecision(traceId).flatMap {
          case None =>
            val decision = spanSampler(traceId, None, false)
            store.storeDecision(traceId, decision).as(decision)
          case Some(decision) => decision.pure[F]
        }
      }

      override def sampleBatch(batch: Batch): F[Option[Batch]] =
        NonEmptyList.fromList(batch.spans.filterNot(_.context.traceFlags.sampled)).flatTraverse { spans =>
          store.batch(spans.map(_.context.traceId)).flatMap { decisions =>
            val (sampledSpans, newDecisions) =
              spans.foldLeft((List.empty[CompletedSpan], Map.empty[TraceId, Boolean])) {
                case ((sampled, computedDecisions), span) =>
                  val traceId = span.context.traceId

                  decisions.get(traceId).orElse(computedDecisions.get(traceId)) match {
                    case None =>
                      val decision = spanSampler(traceId, None, false)

                      val spans = if (decision) sampled else span :: sampled
                      spans -> computedDecisions.updated(traceId, decision)

                    case Some(true) => sampled -> computedDecisions
                    case Some(false) => (span :: sampled) -> computedDecisions
                  }

              }

            store
              .storeDecisions(newDecisions)
              .as(if (sampledSpans.isEmpty) None else Some(Batch(batch.process, sampledSpans)))
          }
        }
    }
  }
}
