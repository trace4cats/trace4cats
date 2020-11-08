package io.janstenpickle.trace4cats.rate.sampling

import cats.Functor
import cats.effect.{Concurrent, Timer}
import cats.syntax.functor._
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanContext, SpanKind, TraceId}
import io.janstenpickle.trace4cats.rate.TokenBucket

import scala.concurrent.duration.FiniteDuration

object RateSpanSampler {
  def apply[F[_]: Functor: TokenBucket]: SpanSampler[F] =
    new SpanSampler[F] {
      override def shouldSample(
        parentContext: Option[SpanContext],
        traceId: TraceId,
        spanName: String,
        spanKind: SpanKind
      ): F[SampleDecision] =
        TokenBucket[F].request1.map {
          case false => SampleDecision.Drop
          case true => SampleDecision.Include
        }
    }

  def create[F[_]: Concurrent: Timer](bucketSize: Int, tokenRate: FiniteDuration): F[SpanSampler[F]] =
    TokenBucket[F](bucketSize, tokenRate).map(implicit tb => apply[F])
}
