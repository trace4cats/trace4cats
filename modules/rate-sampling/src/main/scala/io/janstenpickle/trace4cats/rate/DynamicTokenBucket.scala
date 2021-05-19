package io.janstenpickle.trace4cats.rate

import cats.Applicative
import cats.effect.kernel.Ref
import cats.effect.kernel.syntax.spawn._
import cats.effect.{Resource, Temporal}
import cats.syntax.flatMap._
import cats.syntax.functor._

import scala.concurrent.duration.FiniteDuration

trait DynamicTokenBucket[F[_]] extends TokenBucket[F] {
  def updateConfig(bucketSize: Int, tokenRate: FiniteDuration): F[Unit]
}

object DynamicTokenBucket {
  type CancelToken[F[_]] = F[Unit]

  def create[F[_]: Temporal](bucketSize: Int, tokenRate: FiniteDuration): Resource[F, DynamicTokenBucket[F]] =
    Resource.make(unsafeCreate(bucketSize, tokenRate))(_._2).map(_._1)

  def unsafeCreate[F[_]: Temporal](
    bucketSize: Int,
    tokenRate: FiniteDuration
  ): F[(DynamicTokenBucket[F], CancelToken[F])] =
    for {
      currentConfig <- Ref.of[F, ((Int, FiniteDuration), Boolean)](((bucketSize, tokenRate), false))
      tokens <- Ref.of(bucketSize)
      processFiber <- TokenBucket.bucketProcess(tokens, bucketSize, tokenRate).start

      processFiberRef <- Ref.of(processFiber)
    } yield (
      new DynamicTokenBucket[F] {
        private final val underlying = TokenBucket.impl[F](tokens)

        override def updateConfig(bucketSize: Int, tokenRate: FiniteDuration): F[Unit] =
          currentConfig.get.flatMap { case (conf, isCanceled) =>
            if (conf != (bucketSize -> tokenRate) && !isCanceled) for {
              processFiber <- processFiberRef.get
              _ <- processFiber.cancel
              newProcessFiber <- TokenBucket.bucketProcess(tokens, bucketSize, tokenRate).start
              _ <- processFiberRef.set(newProcessFiber)
            } yield ()
            else Applicative[F].unit
          }

        override def request1: F[Boolean] = underlying.request1

        override def request(n: Int): F[Int] = underlying.request(n)
      },
      for {
        fiber <- processFiberRef.get
        _ <- currentConfig.update { case (conf, _) => (conf, true) }
        _ <- fiber.cancel
      } yield ()
    )
}
