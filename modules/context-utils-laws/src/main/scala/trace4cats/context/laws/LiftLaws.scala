package trace4cats.context.laws

import cats.Monad
import cats.laws.{IsEq, IsEqArrow}
import cats.syntax.applicative._
import cats.syntax.flatMap._
import trace4cats.context.Lift

trait LiftLaws[Low[_], F[_]] {
  implicit def instance: Lift[Low, F]

  implicit def Low: Monad[Low] = instance.Low
  implicit def F: Monad[F] = instance.F

  // external laws:
  def liftIdentity[A](a: A): IsEq[F[A]] =
    instance.lift(a.pure[Low]) <-> a.pure[F]

  def liftComposition[A, B](la: Low[A], f: A => Low[B]): IsEq[F[B]] =
    instance.lift(la).flatMap(a => instance.lift(f(a))) <-> instance.lift(la.flatMap(f))
}

object LiftLaws {
  def apply[Low[_], F[_]](implicit instance0: Lift[Low, F]): LiftLaws[Low, F] = {
    new LiftLaws[Low, F] {
      override val instance = instance0
    }
  }
}
