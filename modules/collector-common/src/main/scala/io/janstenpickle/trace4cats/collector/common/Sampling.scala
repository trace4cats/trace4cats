package io.janstenpickle.trace4cats.collector.common

import cats.Applicative
import cats.effect.{Clock, Sync}
import cats.syntax.functor._
import cats.syntax.semigroup._
import io.janstenpickle.trace4cats.`export`.StreamSpanExporter
import io.janstenpickle.trace4cats.collector.common.config.SamplingConfig
import io.janstenpickle.trace4cats.sampling.tail.cache.LocalCacheSampleDecisionStore
import io.janstenpickle.trace4cats.sampling.tail.{TailSamplingSpanExporter, TailSpanSampler}

import scala.concurrent.duration._

object Sampling {
  def exporter[F[_]: Sync: Clock](
    config: SamplingConfig,
    underlying: StreamSpanExporter[F]
  ): F[StreamSpanExporter[F]] = {
    def decisionStore(ttl: Int, maxSize: Long) =
      LocalCacheSampleDecisionStore[F](ttl.minutes, Some(maxSize))

    val makeExporter = TailSamplingSpanExporter[F](underlying, _)

    config match {
      case SamplingConfig(None, None, _, _) => Applicative[F].pure(underlying)
      case SamplingConfig(Some(probability), None, ttl, maxSize) =>
        decisionStore(ttl, maxSize).map(TailSpanSampler.probabilistic[F](_, probability)).map(makeExporter)
      case SamplingConfig(None, Some(names), ttl, maxSize) =>
        decisionStore(ttl, maxSize).map(TailSpanSampler.spanNameFilter[F](_, names)).map(makeExporter)
      case SamplingConfig(Some(probability), Some(names), ttl, maxSize) =>
        decisionStore(ttl, maxSize)
          .map { store =>
            TailSpanSampler.probabilistic[F](store, probability) |+| TailSpanSampler.spanNameFilter[F](store, names)
          }
          .map(makeExporter)
    }
  }

}
