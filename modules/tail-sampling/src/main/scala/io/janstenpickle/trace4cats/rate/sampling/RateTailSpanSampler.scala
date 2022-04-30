package io.janstenpickle.trace4cats.rate.sampling

import cats.effect.kernel.{Resource, Temporal}
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.{Applicative, Foldable, Monad, MonoidK}
import trace4cats.model.{CompletedSpan, SampleDecision, TraceId}
import io.janstenpickle.trace4cats.rate.TokenBucket
import io.janstenpickle.trace4cats.sampling.tail.{SampleDecisionStore, TailSpanSampler}

import scala.concurrent.duration.FiniteDuration

object RateTailSpanSampler {
  def apply[F[_]: Monad: TokenBucket, G[_]: Applicative: Foldable: MonoidK](
    store: SampleDecisionStore[F]
  ): TailSpanSampler[F, G] =
    TailSpanSampler.storedBatchComputation[F, G](
      store,
      _ =>
        TokenBucket[F].request1.map {
          case false => SampleDecision.Drop
          case true => SampleDecision.Include
        },
      (batch, traceIds) => {
        TokenBucket[F].request(traceIds.size).map { tokens =>
          val sampled = traceIds.take(tokens)

          batch.foldLeft((MonoidK[G].empty[CompletedSpan], Map.empty[TraceId, SampleDecision])) {
            case ((spans, decisions), span) =>
              val traceId = span.context.traceId

              decisions.get(traceId) match {
                case Some(SampleDecision.Drop) => (spans, decisions)
                case Some(SampleDecision.Include) => (TailSpanSampler.combine(span, spans), decisions)
                case None =>
                  if (sampled.contains(traceId))
                    (TailSpanSampler.combine(span, spans), decisions.updated(traceId, SampleDecision.Include))
                  else (spans, decisions.updated(traceId, SampleDecision.Drop))
              }
          }

        }
      }
    )

  def create[F[_]: Temporal, G[_]: Applicative: Foldable: MonoidK](
    store: SampleDecisionStore[F],
    bucketSize: Int,
    tokenRate: FiniteDuration
  ): Resource[F, TailSpanSampler[F, G]] =
    TokenBucket.create[F](bucketSize, tokenRate).map(implicit tb => apply[F, G](store))
}
