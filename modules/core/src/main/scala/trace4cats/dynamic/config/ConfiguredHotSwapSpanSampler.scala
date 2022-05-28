package trace4cats.dynamic.config

import cats.effect.kernel.{Resource, Temporal}
import trace4cats.dynamic.HotSwapSpanSampler
import trace4cats.kernel.SpanSampler
import trace4cats.model.{SampleDecision, SpanContext, SpanKind, TraceId}

trait ConfiguredHotSwapSpanSampler[F[_]] extends SpanSampler[F] {
  def updateConfig(config: SamplerConfig): F[Boolean]
  def getConfig: F[SamplerConfig]
}

object ConfiguredHotSwapSpanSampler {
  def apply[F[_]: Temporal](initialConfig: SamplerConfig): Resource[F, ConfiguredHotSwapSpanSampler[F]] =
    HotSwapSpanSampler(initialConfig)(makeSampler[F])
      .map(underlying =>
        new ConfiguredHotSwapSpanSampler[F] {
          override def updateConfig(config: SamplerConfig): F[Boolean] =
            underlying.swap(config)

          override def getConfig: F[SamplerConfig] = underlying.getConfig

          override def shouldSample(
            parentContext: Option[SpanContext],
            traceId: TraceId,
            spanName: String,
            spanKind: SpanKind
          ): F[SampleDecision] = underlying.shouldSample(parentContext, traceId, spanName, spanKind)
        }
      )
}
