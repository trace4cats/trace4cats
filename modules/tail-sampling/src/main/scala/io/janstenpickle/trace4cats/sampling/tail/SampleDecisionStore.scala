package io.janstenpickle.trace4cats.sampling.tail

import java.time.Duration

import cats.data.NonEmptyList
import cats.effect.{Clock, Sync}
import cats.syntax.functor._
import com.github.benmanes.caffeine.cache.Caffeine
import io.janstenpickle.trace4cats.model.TraceId

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

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
      .delay(
        Caffeine
          .newBuilder()
          .expireAfterAccess(Duration.ofMillis(ttl.toMillis))
          .build[TraceId with Object, Boolean with Object]
      )
      .map { cache =>
        new SampleDecisionStore[F] {
          override def getDecision(traceId: TraceId): F[Option[Boolean]] =
            Sync[F].delay(Option(cache.getIfPresent(traceId)))

          override def storeDecision(traceId: TraceId, sampleDecision: Boolean): F[Unit] =
            Sync[F].delay(
              cache.put(traceId.asInstanceOf[TraceId with Object], sampleDecision.asInstanceOf[Boolean with Object])
            )

          override def batch(traceIds: NonEmptyList[TraceId]): F[Map[TraceId, Boolean]] =
            Sync[F].delay(cache.getAllPresent(traceIds.toList.asJava).asScala.toMap)

          override def storeDecisions(decisions: Map[TraceId, Boolean]): F[Unit] =
            Sync[F].delay(cache.putAll(decisions.asInstanceOf[Map[TraceId with Object, Boolean with Object]].asJava))
        }

      }
}
