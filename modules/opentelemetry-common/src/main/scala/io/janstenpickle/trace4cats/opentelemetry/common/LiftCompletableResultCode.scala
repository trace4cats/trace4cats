package io.janstenpickle.trace4cats.opentelemetry.common

import cats.effect.kernel.Async
import cats.syntax.flatMap._
import io.opentelemetry.sdk.common.CompletableResultCode

trait LiftCompletableResultCode[F[_]] {
  def lift(fa: F[CompletableResultCode])(onFailure: => Throwable): F[Unit]
}

object LiftCompletableResultCode {
  def apply[F[_]](implicit ev: LiftCompletableResultCode[F]): LiftCompletableResultCode[F] = ev

  implicit def fromAsync[F[_]: Async]: LiftCompletableResultCode[F] = new LiftCompletableResultCode[F] {
    def lift(fa: F[CompletableResultCode])(onFailure: => Throwable): F[Unit] =
      fa.flatMap { result =>
        Async[F].async_[Unit] { cb =>
          val _ = result.whenComplete { () =>
            if (result.isSuccess) cb(Right(()))
            else cb(Left(onFailure))
          }
        }
      }
  }

  implicit class Ops[F[_]](private val fa: F[CompletableResultCode]) extends AnyVal {
    def liftResultCode(onFailure: => Throwable)(implicit F: LiftCompletableResultCode[F]): F[Unit] =
      F.lift(fa)(onFailure)
  }
}
