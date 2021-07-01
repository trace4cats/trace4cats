package io.janstenpickle.trace4cats.base.context

import cats.{~>, Monad}

trait Lift[Low[_], F[_]] extends ContextRoot { self =>
  def Low: Monad[Low]
  def F: Monad[F]

  def lift[A](la: Low[A]): F[A]
  def liftK: Low ~> F = new (Low ~> F) {
    override def apply[A](la: Low[A]): F[A] = lift(la)
  }

  def mapK[G[_]: Monad](fk: F ~> G): Lift[Low, G] = new Lift[Low, G] {
    def Low: Monad[Low] = self.Low
    def F: Monad[G] = implicitly
    def lift[A](la: Low[A]): G[A] = fk(self.lift(la))
  }
}

object Lift {
  def apply[Low[_], F[_]](implicit ev: Lift[Low, F]): Lift[Low, F] = ev
}
