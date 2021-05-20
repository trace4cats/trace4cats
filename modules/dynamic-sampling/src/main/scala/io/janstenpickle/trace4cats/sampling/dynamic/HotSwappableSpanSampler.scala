package io.janstenpickle.trace4cats.sampling.dynamic

import cats.Applicative
import cats.effect.kernel.{Ref, Resource, Temporal}
import cats.effect.std.Hotswap
import cats.syntax.flatMap._
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanContext, SpanKind, TraceId}

trait HotSwappableSpanSampler[F[_]] extends SpanSampler[F] {
  def updateConfig(config: SamplerConfig): F[Unit]
  def getConfig: F[SamplerConfig]
}

object HotSwappableSpanSampler {
  def create[F[_]: Temporal](config: SamplerConfig): Resource[F, HotSwappableSpanSampler[F]] = for {
    currentConfig <- Resource.eval(Ref.of(config))
    (hotswap, sampler) <- Hotswap(SamplerUtil.makeSampler[F](config))
    currentSampler <- Resource.eval(Ref.of(sampler))
  } yield new HotSwappableSpanSampler[F] {
    override def updateConfig(newConfig: SamplerConfig): F[Unit] = currentConfig.get.flatMap { oldConfig =>
      Applicative[F].whenA(oldConfig != newConfig) {
        hotswap.swap(SamplerUtil.makeSampler[F](config).evalTap(currentSampler.set))
      }
    }

    override def getConfig: F[SamplerConfig] = currentConfig.get

    override def shouldSample(
      parentContext: Option[SpanContext],
      traceId: TraceId,
      spanName: String,
      spanKind: SpanKind
    ): F[SampleDecision] = currentSampler.get.flatMap(_.shouldSample(parentContext, traceId, spanName, spanKind))

  }
}
