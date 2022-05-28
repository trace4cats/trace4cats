package trace4cats.dynamic

import cats.effect.kernel.{Resource, Temporal}
import cats.syntax.applicative._
import trace4cats.RateSpanSampler
import trace4cats.kernel.SpanSampler
import trace4cats.rate.{DynamicTokenBucket, TokenInterval}

package object config {
  private[config] def makeSampler[F[_]: Temporal](config: SamplerConfig): Resource[F, SpanSampler[F]] = {
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
