package io.janstenpickle.trace4cats.sampling.dynamic

import cats.effect.kernel.Temporal
import cats.syntax.applicative._
import cats.syntax.functor._
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.rate.sampling.RateSpanSampler
import io.janstenpickle.trace4cats.rate.{DynamicTokenBucket, TokenInterval}

object SamplerUtil {
  private[dynamic] def makeSampler[F[_]: Temporal](config: SamplerConfig): F[SamplerProcess[F]] = config match {
    case SamplerConfig.Always => SamplerProcess(SpanSampler.always[F], ().pure).pure
    case SamplerConfig.Never => SamplerProcess(SpanSampler.never[F], ().pure).pure
    case SamplerConfig.Probabilistic(probability, rootSpansOnly) =>
      SamplerProcess(SpanSampler.probabilistic[F](probability, rootSpansOnly), ().pure).pure
    case SamplerConfig.Rate(bucketSize, tokenRate) =>
      TokenInterval(tokenRate)
        .fold(SamplerProcess(SpanSampler.always[F], ().pure).pure)(interval =>
          DynamicTokenBucket.unsafeCreate[F](bucketSize, interval).map { case (tb, cancel) =>
            implicit val tokenBucket: DynamicTokenBucket[F] = tb

            SamplerProcess(RateSpanSampler[F], cancel)
          }
        )
  }
}
