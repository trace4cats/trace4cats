package io.janstenpickle.trace4cats.base.context

import cats.{~>, Monad}
import cats.arrow.FunctionK

trait Unlift[Low[_], F[_]] extends Lift[Low, F] { self =>
  def askUnlift: F[F ~> Low]

  def withUnlift[A](f: F ~> Low => Low[A]): F[A] = F.flatMap(askUnlift)(f.andThen(lift))

  def imapK[G[_]: Monad](fk: F ~> G, gk: G ~> F): Unlift[Low, G] = new Unlift[Low, G] {
    def Low: Monad[Low] = self.Low
    def F: Monad[G] = implicitly
    def lift[A](la: Low[A]): G[A] = fk(self.lift(la))
    def askUnlift: G[G ~> Low] = fk(self.F.map(self.askUnlift)(_.compose(gk)))
  }
}

object Unlift {
  def apply[Low[_], F[_]](implicit ev: Unlift[Low, F]): Unlift[Low, F] = ev

  trait IdUnlift[F[_]] extends Lift.IdLift[F] with Unlift[F, F] {
    def askUnlift: F[F ~> F] = F.pure(FunctionK.id[F])
  }

  implicit def idUnlift[F[_]](implicit M: Monad[F]): Unlift[F, F] = new IdUnlift[F] { val F = M }
}
