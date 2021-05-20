package io.janstenpickle.trace4cats.sampling.dynamic

import cats.Applicative
import cats.effect.kernel.{Ref, Resource, Temporal}
import cats.effect.std.Hotswap
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanContext, SpanKind, TraceId}

trait HotSwappableSpanSampler[F[_]] extends SpanSampler[F] {
  def updateConfig(config: SamplerConfig): F[Unit]
  def getConfig: F[SamplerConfig]
}

object HotSwappableSpanSampler {
  def create[F[_]: Temporal](config: SamplerConfig): Resource[F, HotSwappableSpanSampler[F]] = for {
    (hotswap, sampler) <- Hotswap(SamplerUtil.makeSampler[F](config))
    current <- Resource.eval(Ref.of((sampler, config)))
  } yield new HotSwappableSpanSampler[F] {
    override def updateConfig(newConfig: SamplerConfig): F[Unit] = current.get.flatMap { case (_, oldConfig) =>
      Applicative[F].whenA(oldConfig != newConfig) {
        hotswap.swap(SamplerUtil.makeSampler[F](newConfig).evalTap(newSampler => current.set((newSampler, newConfig))))
      }
    }

    override def getConfig: F[SamplerConfig] = current.get.map { case (_, config) => config }

    override def shouldSample(
      parentContext: Option[SpanContext],
      traceId: TraceId,
      spanName: String,
      spanKind: SpanKind
    ): F[SampleDecision] = current.get.flatMap { case (sampler, _) =>
      sampler.shouldSample(parentContext, traceId, spanName, spanKind)
    }

  }
}
