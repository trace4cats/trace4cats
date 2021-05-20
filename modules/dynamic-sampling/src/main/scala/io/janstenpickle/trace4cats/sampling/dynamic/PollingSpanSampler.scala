package io.janstenpickle.trace4cats.sampling.dynamic

import cats.effect.kernel.syntax.spawn._
import cats.effect.kernel.{Resource, Temporal}
import cats.kernel.Eq
import fs2.Stream
import io.janstenpickle.trace4cats.kernel.SpanSampler

import scala.concurrent.duration.FiniteDuration

object PollingSpanSampler {
  def create[F[_]: Temporal, A: Eq](
    configuredSampler: F[(A, Resource[F, SpanSampler[F]])],
    updateInterval: FiniteDuration
  ): Resource[F, SpanSampler[F]] = {

    def configPoller(sampler: HotSwapSpanSampler[F, A]): Stream[F, Unit] =
      for {
        _ <- Stream.fixedRate[F](updateInterval)
        (id, samplerResource) <- Stream.eval(configuredSampler)
        _ <- Stream.eval(sampler.updateSampler(id, samplerResource))
      } yield ()

    for {
      (id, samplerResource) <- Resource.eval(configuredSampler)
      sampler <- HotSwapSpanSampler.create(id, samplerResource)
      _ <- configPoller(sampler).compile.drain.background
    } yield sampler
  }
}
