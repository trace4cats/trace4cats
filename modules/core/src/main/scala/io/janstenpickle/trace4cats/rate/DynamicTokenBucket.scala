package io.janstenpickle.trace4cats.rate

import cats.Applicative
import cats.effect.kernel.syntax.spawn._
import cats.effect.kernel.{Ref, Resource, Temporal}
import cats.effect.std.Hotswap
import cats.syntax.flatMap._
import cats.syntax.functor._

import scala.concurrent.duration.FiniteDuration

trait DynamicTokenBucket[F[_]] extends TokenBucket[F] {
  def updateConfig(bucketSize: Int, tokenRate: FiniteDuration): F[Unit]
}

object DynamicTokenBucket {
  def create[F[_]: Temporal](bucketSize: Int, tokenRate: FiniteDuration): Resource[F, DynamicTokenBucket[F]] =
    for {
      currentConfig <- Resource.eval(Ref.of[F, (Int, FiniteDuration)]((bucketSize, tokenRate)))
      tokens <- Resource.eval(Ref.of(bucketSize))
      hotswap <- Hotswap(TokenBucket.bucketProcess(tokens, bucketSize, tokenRate).background.void).map(_._1)
    } yield new DynamicTokenBucket[F] {
      private final val underlying = TokenBucket.impl[F](tokens)

      override def updateConfig(bucketSize: Int, tokenRate: FiniteDuration): F[Unit] =
        currentConfig.get.flatMap { oldConfig =>
          Applicative[F].whenA(oldConfig != (bucketSize -> tokenRate)) {
            hotswap.swap(
              TokenBucket
                .bucketProcess(tokens, bucketSize, tokenRate)
                .background
                .void
                .evalTap(_ => currentConfig.set((bucketSize, tokenRate)))
            )
          }
        }

      override def request1: F[Boolean] = underlying.request1

      override def request(n: Int): F[Int] = underlying.request(n)
    }
}
