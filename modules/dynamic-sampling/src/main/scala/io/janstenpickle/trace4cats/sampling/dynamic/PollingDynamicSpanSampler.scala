package io.janstenpickle.trace4cats.sampling.dynamic

import cats.effect.kernel.syntax.spawn._
import cats.effect.kernel.{Resource, Temporal}
import fs2.Stream
import io.janstenpickle.trace4cats.kernel.SpanSampler

import scala.concurrent.duration.FiniteDuration

object PollingDynamicSpanSampler {
  def create[F[_]: Temporal, A](
    configuredSampler: F[(A, Resource[F, SpanSampler[F]])],
    updateInterval: FiniteDuration
  ): Resource[F, SpanSampler[F]] = {

    def configPoller(sampler: HotSwappableDynamicSpanSampler[F, A]): Stream[F, Unit] =
      for {
        _ <- Stream.fixedRate[F](updateInterval)
        (id, samplerResource) <- Stream.eval(configuredSampler)
        _ <- Stream.eval(sampler.updateSampler(id, samplerResource))
      } yield ()

    for {
      (id, samplerResource) <- Resource.eval(configuredSampler)
      sampler <- HotSwappableDynamicSpanSampler.create(id, samplerResource)
      _ <- configPoller(sampler).compile.drain.background
    } yield sampler
  }
}
