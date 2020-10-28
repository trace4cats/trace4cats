package io.janstenpickle.trace4cats.sampling.tail

import cats.data.NonEmptyList
import cats.effect.{Clock, Sync}
import cats.syntax.functor._
import com.github.blemale.scaffeine.Scaffeine
import io.janstenpickle.trace4cats.model.TraceId

import scala.concurrent.duration._

trait SampleDecisionStore[F[_]] {
  def getDecision(traceId: TraceId): F[Option[Boolean]]
  def batch(traceIds: NonEmptyList[TraceId]): F[Map[TraceId, Boolean]]
  def storeDecision(traceId: TraceId, sampleDecision: Boolean): F[Unit]
  def storeDecisions(decisions: Map[TraceId, Boolean]): F[Unit]
}

object SampleDecisionStore {
  def apply[F[_]](implicit store: SampleDecisionStore[F]): SampleDecisionStore[F] = store

  def localCache[F[_]: Sync: Clock](ttl: FiniteDuration = 5.minutes): F[SampleDecisionStore[F]] =
    Sync[F]
      .delay(Scaffeine().expireAfterAccess(ttl).build[TraceId, Boolean])
      .map { cache =>
        new SampleDecisionStore[F] {
          override def getDecision(traceId: TraceId): F[Option[Boolean]] =
            Sync[F].delay(cache.getIfPresent(traceId))

          override def storeDecision(traceId: TraceId, sampleDecision: Boolean): F[Unit] =
            Sync[F].delay(cache.put(traceId, sampleDecision))

          override def batch(traceIds: NonEmptyList[TraceId]): F[Map[TraceId, Boolean]] =
            Sync[F].delay(cache.getAllPresent(traceIds.toList))

          override def storeDecisions(decisions: Map[TraceId, Boolean]): F[Unit] =
            Sync[F].delay(cache.putAll(decisions))
        }

      }
}
