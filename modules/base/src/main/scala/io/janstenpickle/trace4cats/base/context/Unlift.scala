package io.janstenpickle.trace4cats.base.context

import cats.{~>, Monad}

trait Unlift[Low[_], F[_]] extends Lift[Low, F] {
  def F: Monad[F]

  def askUnlift: F[F ~> Low]

  def withUnlift[A](f: F ~> Low => Low[A]): F[A] = F.flatMap(askUnlift)(f.andThen(lift))
}

object Unlift {
  def apply[Low[_], F[_]](implicit ev: Unlift[Low, F]): Unlift[Low, F] = ev
}
