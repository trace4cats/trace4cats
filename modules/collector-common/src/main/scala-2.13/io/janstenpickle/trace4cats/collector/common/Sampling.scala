package io.janstenpickle.trace4cats.collector.common

import cats.effect.kernel.{Async, Resource}
import cats.syntax.traverse._
import cats.{Parallel, Semigroup}
import fs2.{Chunk, Pipe}
import org.typelevel.log4cats.Logger
import io.janstenpickle.trace4cats.collector.common.config.{RedisStoreConfig, SamplingConfig}
import io.janstenpickle.trace4cats.model.CompletedSpan
import io.janstenpickle.trace4cats.rate.sampling.RateTailSpanSampler
import io.janstenpickle.trace4cats.sampling.tail.cache.LocalCacheSampleDecisionStore
import io.janstenpickle.trace4cats.sampling.tail.redis.RedisSampleDecisionStore
import io.janstenpickle.trace4cats.sampling.tail.{TailSamplingPipe, TailSpanSampler}

import scala.concurrent.duration._

object Sampling {
  def pipe[F[_]: Async: Parallel: Logger](
    config: SamplingConfig
  ): Resource[F, Pipe[F, CompletedSpan, CompletedSpan]] = {
    def makeDecisionStore(keyPrefix: Short) =
      config.redis match {
        case None =>
          Resource.eval(LocalCacheSampleDecisionStore[F](config.cacheTtlMinutes.minutes, Some(config.maxCacheSize)))
        case Some(RedisStoreConfig.RedisServer(host, port)) =>
          RedisSampleDecisionStore[F](host, port, keyPrefix, config.cacheTtlMinutes.minutes, Some(config.maxCacheSize))
        case Some(RedisStoreConfig.RedisCluster(servers)) =>
          RedisSampleDecisionStore
            .cluster[F](
              servers.map(s => s.host -> s.port),
              keyPrefix,
              config.cacheTtlMinutes.minutes,
              Some(config.maxCacheSize)
            )
      }

    val prob: Option[TailSpanSampler[F, Chunk]] = config.sampleProbability.map { probability =>
      TailSpanSampler.probabilistic[F, Chunk](probability)
    }

    for {
      name <- config.dropSpanNames.traverse { names =>
        makeDecisionStore(0).map(TailSpanSampler.spanNameDrop[F, Chunk](_, names))
      }

      rate <- config.rate.traverse { rate =>
        makeDecisionStore(1)
          .flatMap(RateTailSpanSampler.create[F, Chunk](_, rate.maxBatchSize, rate.tokenRateMillis.millis))
      }
    } yield Semigroup[TailSpanSampler[F, Chunk]]
      .combineAllOption(List(prob, name, rate).flatten)
      .fold[Pipe[F, CompletedSpan, CompletedSpan]](identity)(TailSamplingPipe[F])

  }

}
