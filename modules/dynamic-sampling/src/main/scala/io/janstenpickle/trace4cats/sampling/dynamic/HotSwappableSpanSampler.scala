package io.janstenpickle.trace4cats.sampling.dynamic

import cats.effect.kernel.Temporal
import cats.effect.{Ref, Resource}
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanContext, SpanKind, TraceId}

trait HotSwappableSpanSampler[F[_]] extends SpanSampler[F] {
  def updateConfig(config: SamplerConfig): F[Unit]
}

object HotSwappableSpanSampler {
  def create[F[_]: Temporal](config: SamplerConfig): Resource[F, HotSwappableSpanSampler[F]] = for {
    currentConfig <- Resource.eval(Ref.of(config))
    sampler <- Resource.make(SamplerUtil.makeSampler[F](config).flatMap(Ref.of(_)))(_.get.flatMap(_.cancel))
  } yield new HotSwappableSpanSampler[F] {
    override def updateConfig(config: SamplerConfig): F[Unit] = currentConfig.get.flatMap { curr =>
      if (curr != config) for {
        oldSampler <- sampler.get
        newSampler <- SamplerUtil.makeSampler[F](config)
        _ <- sampler.set(newSampler)
        _ <- oldSampler.cancel
        _ <- currentConfig.set(config)
      } yield ()
      else ().pure
    }

    override def shouldSample(
      parentContext: Option[SpanContext],
      traceId: TraceId,
      spanName: String,
      spanKind: SpanKind
    ): F[SampleDecision] = sampler.get.flatMap(_.value.shouldSample(parentContext, traceId, spanName, spanKind))
  }
}
