package io.janstenpickle.trace4cats.inject

import cats.data.Kleisli
import cats.~>
import io.janstenpickle.trace4cats.Span

trait LiftTrace[F[_], G[_]] {
  def apply[A](fa: F[A]): G[A] = lift(fa)
  def lift[A](fa: F[A]): G[A] = fk(fa)
  def fk: F ~> G
}

object LiftTrace {
  def apply[F[_], G[_]](implicit raise: LiftTrace[F, G]): LiftTrace[F, G] = raise

  implicit def kleisliRaise[F[_]]: LiftTrace[F, Kleisli[F, Span[F], *]] =
    new LiftTrace[F, Kleisli[F, Span[F], *]] {
      override val fk: F ~> Kleisli[F, Span[F], *] = Kleisli.liftK
    }
}
