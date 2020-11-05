package io.janstenpickle.trace4cats.sampling.tail.redis

import cats.data.NonEmptyList
import cats.effect.syntax.bracket._
import cats.effect.syntax.concurrent._
import cats.effect.{Concurrent, ContextShift, Fiber, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.parallel._
import cats.{Applicative, Parallel}
import com.github.blemale.scaffeine.Scaffeine
import dev.profunktor.redis4cats.{Redis, RedisCommands}
import dev.profunktor.redis4cats.codecs.Codecs
import dev.profunktor.redis4cats.codecs.splits.SplitEpi
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.log4cats._
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.model.{SampleDecision, TraceId}
import io.janstenpickle.trace4cats.sampling.tail.SampleDecisionStore
import io.lettuce.core.ClientOptions

import scala.concurrent.duration.FiniteDuration

object RedisSampleDecisionStore {
  private val traceIdSplit: SplitEpi[Array[Byte], TraceId] = SplitEpi(TraceId(_).getOrElse(TraceId.invalid), _.value)
  private val booleanSplit: SplitEpi[Array[Byte], SampleDecision] = SplitEpi(
    {
      case Array(1) => SampleDecision.Drop
      case _ => SampleDecision.Include
    },
    {
      case SampleDecision.Drop => Array(1)
      case SampleDecision.Include => Array(0)
    }
  )

  private val codec: RedisCodec[TraceId, SampleDecision] = Codecs.derive(RedisCodec.Bytes, traceIdSplit, booleanSplit)

  def apply[F[_]: Concurrent: Parallel](
    cmd: RedisCommands[F, TraceId, SampleDecision],
    ttl: FiniteDuration,
    maximumLocalCacheSize: Option[Long]
  ): F[SampleDecisionStore[F]] =
    Sync[F]
      .delay {
        val builder = Scaffeine().expireAfterAccess(ttl)

        maximumLocalCacheSize.fold(builder)(builder.maximumSize).build[TraceId, SampleDecision]()
      }
      .map { cache =>
        def cacheDecision(traceId: TraceId, decision: F[Option[SampleDecision]]) =
          decision.flatTap {
            case Some(value) => Sync[F].delay(cache.put(traceId, value))
            case None => Applicative[F].unit
          }

        new SampleDecisionStore[F] {
          override def getDecision(traceId: TraceId): F[Option[SampleDecision]] =
            Sync[F].delay(cache.getIfPresent(traceId)).flatMap {
              case v @ Some(_) => Applicative[F].pure(v)
              case None => cacheDecision(traceId, cmd.get(traceId))
            }

          override def batch(traceIds: NonEmptyList[TraceId]): F[Map[TraceId, SampleDecision]] = {
            val traceIdSet = traceIds.toList.toSet
            for {
              local <- Sync[F].delay(cache.getAllPresent(traceIdSet))
              remainder = traceIdSet.diff(local.keySet)
              remote <- cmd.mGet(remainder)
              _ <- Sync[F].delay(cache.putAll(remote))
            } yield local ++ remote
          }

          override def storeDecision(traceId: TraceId, sampleDecision: SampleDecision): F[Unit] =
            cmd.setEx(traceId, sampleDecision, ttl) >> Sync[F].delay(cache.put(traceId, sampleDecision))

          override def storeDecisions(decisions: Map[TraceId, SampleDecision]): F[Unit] =
            (cmd.disableAutoFlush >> (for {
              results <- decisions.foldLeft(Applicative[F].pure(List.empty[Fiber[F, Unit]])) {
                case (acc, (traceId, decision)) =>
                  cmd.setEx(traceId, decision, ttl).start.flatMap(fiber => acc.map(fiber :: _))
              }
              _ <- cmd.flushCommands
              _ <- results.parTraverse_(_.join)
            } yield ()).guarantee(cmd.enableAutoFlush)) >> Sync[F].delay(cache.putAll(decisions))
        }

      }

  private def redisUrl(host: String, port: Int): String = s"redis://$host:$port"

  def apply[F[_]: Concurrent: ContextShift: Parallel: Logger](
    host: String,
    port: Int,
    ttl: FiniteDuration,
    maximumLocalCacheSize: Option[Long],
    modifyOptions: ClientOptions => ClientOptions = identity
  ): Resource[F, SampleDecisionStore[F]] =
    for {
      opts <- Resource.liftF(Sync[F].delay(modifyOptions(ClientOptions.create())))
      cmd <- Redis[F].withOptions(redisUrl(host, port), opts, codec)
      sampler <- Resource.liftF(apply[F](cmd, ttl, maximumLocalCacheSize))
    } yield sampler

  def cluster[F[_]: Concurrent: ContextShift: Parallel: Logger](
    servers: NonEmptyList[(String, Int)],
    ttl: FiniteDuration,
    maximumLocalCacheSize: Option[Long],
  ): Resource[F, SampleDecisionStore[F]] =
    for {
      cmd <- Redis[F].cluster(codec, servers.map((redisUrl _).tupled).toList: _*)
      sampler <- Resource.liftF(apply[F](cmd, ttl, maximumLocalCacheSize))
    } yield sampler
}
