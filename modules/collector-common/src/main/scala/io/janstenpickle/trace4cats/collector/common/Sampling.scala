package io.janstenpickle.trace4cats.collector.common

import cats.Parallel
import cats.effect.{Clock, Concurrent, ContextShift, Resource}
import cats.syntax.semigroup._
import fs2.Chunk
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.`export`.StreamSpanExporter
import io.janstenpickle.trace4cats.collector.common.config.{RedisStoreConfig, SamplingConfig}
import io.janstenpickle.trace4cats.sampling.tail.cache.LocalCacheSampleDecisionStore
import io.janstenpickle.trace4cats.sampling.tail.redis.RedisSampleDecisionStore
import io.janstenpickle.trace4cats.sampling.tail.{TailSamplingSpanExporter, TailSpanSampler}

import scala.concurrent.duration._

object Sampling {
  def exporter[F[_]: Concurrent: ContextShift: Clock: Parallel: Logger](
    config: SamplingConfig,
    underlying: StreamSpanExporter[F]
  ): Resource[F, StreamSpanExporter[F]] = {
    def decisionStore(ttl: Int, maxSize: Long, redis: Option[RedisStoreConfig]) =
      redis match {
        case None => Resource.liftF(LocalCacheSampleDecisionStore[F](ttl.minutes, Some(maxSize)))
        case Some(RedisStoreConfig.RedisServer(host, port)) =>
          RedisSampleDecisionStore[F](host, port, ttl.minutes, Some(maxSize))
        case Some(RedisStoreConfig.RedisCluster(servers)) =>
          RedisSampleDecisionStore.cluster[F](servers.map(s => s.host -> s.port), ttl.minutes, Some(maxSize))
      }

    val makeExporter = TailSamplingSpanExporter[F](underlying, _: TailSpanSampler[F, Chunk])

    config match {
      case SamplingConfig(None, None, _, _, _) => Resource.pure(underlying)
      case SamplingConfig(Some(probability), None, _, _, _) =>
        Resource.pure(makeExporter(TailSpanSampler.probabilistic[F, Chunk](probability)))
      case SamplingConfig(None, Some(names), ttl, maxSize, redis) =>
        decisionStore(ttl, maxSize, redis).map(TailSpanSampler.spanNameFilter[F, Chunk](_, names)).map(makeExporter)
      case SamplingConfig(Some(probability), Some(names), ttl, maxSize, redis) =>
        decisionStore(ttl, maxSize, redis)
          .map { store =>
            TailSpanSampler.probabilistic[F, Chunk](probability) |+| TailSpanSampler
              .spanNameFilter[F, Chunk](store, names)
          }
          .map(makeExporter)
    }
  }

}
