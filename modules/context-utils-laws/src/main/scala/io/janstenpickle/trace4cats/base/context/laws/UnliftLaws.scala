package io.janstenpickle.trace4cats.base.context
package laws

import cats.laws.{IsEq, IsEqArrow}
import cats.syntax.flatMap._
import cats.{~>, Monad}

trait UnliftLaws[Low[_], F[_]] extends LiftLaws[Low, F] {
  override implicit def instance: Unlift[Low, F]

  override implicit def Low: Monad[Low] = instance.Low
  override implicit def F: Monad[F] = instance.F

  // external laws:
  def unliftIdempotency[A](fa: F[A]): IsEq[F[A]] =
    instance.askUnlift.flatMap(lower => instance.lift(lower(fa))) <-> fa

  // internal law:
  def withUnliftIsAskUnliftAndFlatMap[A](f: F ~> Low => Low[A]): IsEq[F[A]] =
    instance.askUnlift.flatMap(lower => instance.lift(f(lower))) <-> instance.withUnlift(f)
}

object UnliftLaws {
  def apply[Low[_], F[_]](implicit instance0: Unlift[Low, F]): UnliftLaws[Low, F] = {
    new UnliftLaws[Low, F] {
      override val instance = instance0
    }
  }
}
