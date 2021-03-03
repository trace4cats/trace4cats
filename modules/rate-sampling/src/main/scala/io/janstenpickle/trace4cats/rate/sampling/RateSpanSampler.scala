package io.janstenpickle.trace4cats.rate.sampling

import cats.{Applicative, Functor}
import cats.effect.kernel.Temporal
import cats.syntax.functor._
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanContext, SpanKind, TraceId}
import io.janstenpickle.trace4cats.rate.TokenBucket

import scala.concurrent.duration._

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

  def create[F[_]: Temporal](bucketSize: Int, tokenRate: Double): F[SpanSampler[F]] =
    Some(1.second / tokenRate)
      .collect { case dur: FiniteDuration => dur }
      .fold(Applicative[F].pure(SpanSampler.always[F]))(create[F](bucketSize, _))

  def create[F[_]: Temporal](bucketSize: Int, tokenInterval: FiniteDuration): F[SpanSampler[F]] =
    TokenBucket[F](bucketSize, tokenInterval).map(implicit tb => apply[F])
}
