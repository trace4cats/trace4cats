package io.janstenpickle.trace4cats.sampling.dynamic

import cats.effect.kernel.syntax.spawn._
import cats.effect.{Resource, Temporal}
import fs2.Stream
import io.janstenpickle.trace4cats.kernel.SpanSampler

import scala.concurrent.duration.FiniteDuration

object ConfigPollingSpanSampler {
  def create[F[_]: Temporal](
    config: F[SamplerConfig],
    configUpdateInterval: FiniteDuration
  ): Resource[F, SpanSampler[F]] = {

    def configPoller(sampler: HotSwappableSpanSampler[F]): Stream[F, Unit] =
      for {
        _ <- Stream.fixedRate[F](configUpdateInterval)
        conf <- Stream.eval(config)
        _ <- Stream.eval(sampler.updateConfig(conf))
      } yield ()

    for {
      initialConfig <- Resource.eval(config)
      sampler <- HotSwappableSpanSampler.create(initialConfig)
      _ <- configPoller(sampler).compile.drain.background
    } yield sampler
  }
}
