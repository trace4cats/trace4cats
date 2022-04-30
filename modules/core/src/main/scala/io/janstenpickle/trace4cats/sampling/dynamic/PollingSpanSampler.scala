package io.janstenpickle.trace4cats.sampling.dynamic

import cats.effect.kernel.syntax.spawn._
import cats.effect.kernel.{Resource, Temporal}
import cats.kernel.Eq
import fs2.Stream
import trace4cats.kernel.SpanSampler

import scala.concurrent.duration.FiniteDuration

object PollingSpanSampler {
  def apply[F[_]: Temporal, A: Eq](configSource: F[A], updateInterval: FiniteDuration)(
    makeSampler: A => Resource[F, SpanSampler[F]]
  ): Resource[F, SpanSampler[F]] = {

    def configPoller(sampler: HotSwapSpanSampler[F, A]): Stream[F, Unit] =
      for {
        _ <- Stream.fixedRate[F](updateInterval)
        config <- Stream.eval(configSource)
        _ <- Stream.eval(sampler.swap(config))
      } yield ()

    for {
      config <- Resource.eval(configSource)
      sampler <- HotSwapSpanSampler(config)(makeSampler)
      _ <- configPoller(sampler).compile.drain.background
    } yield sampler
  }
}
