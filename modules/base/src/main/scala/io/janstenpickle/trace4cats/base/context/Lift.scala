package io.janstenpickle.trace4cats.base.context

import cats.{~>, Monad}

trait Lift[Low[_], F[_]] extends ContextRoot {
  def Low: Monad[Low]
  def F: Monad[F]

  def lift[A](la: Low[A]): F[A]
  def liftK: Low ~> F = λ[Low ~> F](lift(_))
}

object Lift {
  def apply[Low[_], F[_]](implicit ev: Lift[Low, F]): Lift[Low, F] = ev
}
