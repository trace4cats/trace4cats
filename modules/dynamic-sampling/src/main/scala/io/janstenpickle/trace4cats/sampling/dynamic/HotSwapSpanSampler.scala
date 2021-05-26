package io.janstenpickle.trace4cats.sampling.dynamic

import cats.effect.kernel.{Resource, Temporal}
import cats.kernel.Eq
import io.janstenpickle.trace4cats.hotswap.HotswapConstructor
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanContext, SpanKind, TraceId}

trait HotSwapSpanSampler[F[_], A] extends SpanSampler[F] {
  def swap(samplerConfig: A): F[Boolean]
  def getConfig: F[A]
}

object HotSwapSpanSampler {
  def apply[F[_]: Temporal, A: Eq](
    initial: A
  )(make: A => Resource[F, SpanSampler[F]]): Resource[F, HotSwapSpanSampler[F, A]] =
    HotswapConstructor[F, A, SpanSampler[F]](initial)(make).map { hotswap =>
      new HotSwapSpanSampler[F, A] {
        override def swap(samplerConfig: A): F[Boolean] = hotswap.swap(samplerConfig)

        override def getConfig: F[A] = hotswap.currentParams

        override def shouldSample(
          parentContext: Option[SpanContext],
          traceId: TraceId,
          spanName: String,
          spanKind: SpanKind
        ): F[SampleDecision] =
          hotswap.resource.use(_.shouldSample(parentContext, traceId, spanName, spanKind))
      }
    }
}
