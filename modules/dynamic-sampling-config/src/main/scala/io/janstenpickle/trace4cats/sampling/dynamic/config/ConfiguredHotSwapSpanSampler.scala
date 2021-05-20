package io.janstenpickle.trace4cats.sampling.dynamic.config

import cats.effect.kernel.{Resource, Temporal}
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanContext, SpanKind, TraceId}
import io.janstenpickle.trace4cats.sampling.dynamic.HotSwapSpanSampler

trait ConfiguredHotSwapSpanSampler[F[_]] extends SpanSampler[F] {
  def updateConfig(config: SamplerConfig): F[Boolean]
  def getConfig: F[SamplerConfig]
}

object ConfiguredHotSwapSpanSampler {
  def create[F[_]: Temporal](initialConfig: SamplerConfig): Resource[F, ConfiguredHotSwapSpanSampler[F]] =
    HotSwapSpanSampler
      .create(initialConfig, SamplerUtil.makeSampler(initialConfig))
      .map(underlying =>
        new ConfiguredHotSwapSpanSampler[F] {
          override def updateConfig(config: SamplerConfig): F[Boolean] =
            underlying.updateSampler(config, SamplerUtil.makeSampler(config))

          override def getConfig: F[SamplerConfig] = underlying.getId

          override def shouldSample(
            parentContext: Option[SpanContext],
            traceId: TraceId,
            spanName: String,
            spanKind: SpanKind
          ): F[SampleDecision] = underlying.shouldSample(parentContext, traceId, spanName, spanKind)
        }
      )
}
