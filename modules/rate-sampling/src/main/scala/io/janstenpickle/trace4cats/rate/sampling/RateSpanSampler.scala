package io.janstenpickle.trace4cats.rate.sampling

import cats.Functor
import cats.effect.kernel.{Resource, Temporal}
import cats.syntax.functor._
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanContext, SpanKind, TraceId}
import io.janstenpickle.trace4cats.rate.{TokenBucket, TokenInterval}

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

  def create[F[_]: Temporal](bucketSize: Int, tokenRate: Double): Resource[F, SpanSampler[F]] =
    TokenInterval(tokenRate)
      .fold(Resource.pure[F, SpanSampler[F]](SpanSampler.always[F]))(create[F](bucketSize, _))

  def create[F[_]: Temporal](bucketSize: Int, tokenInterval: FiniteDuration): Resource[F, SpanSampler[F]] =
    TokenBucket.create[F](bucketSize, tokenInterval).map(implicit tb => apply[F])
}
