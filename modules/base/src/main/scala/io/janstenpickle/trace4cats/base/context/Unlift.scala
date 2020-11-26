package io.janstenpickle.trace4cats.base.context

import cats.{~>, Monad}

trait Unlift[Low[_], F[_]] extends Lift[Low, F] {
  protected def F: Monad[F]

  def unlift: F[F ~> Low]

  def disclose[A](f: F ~> Low => Low[A]): F[A] = F.flatMap(unlift)(f.andThen(lift))
}

object Unlift {
  def apply[Low[_], F[_]](implicit ev: Unlift[Low, F]): Unlift[Low, F] = ev
}
