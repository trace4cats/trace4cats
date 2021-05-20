package io.janstenpickle.trace4cats.sampling.dynamic.config

import cats.effect.kernel.{Resource, Temporal}
import cats.syntax.applicative._
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.rate.sampling.RateSpanSampler
import io.janstenpickle.trace4cats.rate.{DynamicTokenBucket, TokenInterval}

object SamplerUtil {
  private[dynamic] def makeSampler[F[_]: Temporal](config: SamplerConfig): Resource[F, SpanSampler[F]] = {
    type R[A] = Resource[F, A]
    config match {
      case SamplerConfig.Always => SpanSampler.always[F].pure[R]
      case SamplerConfig.Never => SpanSampler.never[F].pure[R]
      case SamplerConfig.Probabilistic(probability, rootSpansOnly) =>
        SpanSampler.probabilistic[F](probability, rootSpansOnly).pure[R]
      case SamplerConfig.Rate(bucketSize, tokenRate, rootSpansOnly) =>
        TokenInterval(tokenRate).fold(SpanSampler.always[F].pure[R]) { interval =>
          DynamicTokenBucket.create[F](bucketSize, interval).map(implicit tb => RateSpanSampler[F](rootSpansOnly))
        }
    }
  }
}
