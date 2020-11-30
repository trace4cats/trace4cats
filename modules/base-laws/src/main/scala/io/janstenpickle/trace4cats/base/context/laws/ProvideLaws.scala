package io.janstenpickle.trace4cats.base.context
package laws

import cats.Monad
import cats.laws.{IsEq, IsEqArrow}
import cats.syntax.flatMap._

trait ProvideLaws[Low[_], F[_], R] extends LocalLaws[F, R] with UnliftLaws[Low, F] {
  override implicit def instance: Provide[Low, F, R]

  override implicit def Low: Monad[Low] = instance.Low
  override implicit def F: Monad[F] = instance.F

  // internal laws:
  def askUnliftIsAccessProvideK[A](fa: F[A]): IsEq[F[A]] =
    instance.access(instance.provideK).flatMap(lower => instance.lift(lower(fa))) <->
      instance.askUnlift.flatMap(lower => instance.lift(lower(fa)))

  def kleisliftIsLiftAndAccessF[A](f: R => Low[A]): IsEq[F[A]] =
    instance.accessF(r => instance.lift(f(r))) <-> instance.kleislift(f)
}

object ProvideLaws {
  def apply[Low[_], F[_], R](implicit instance0: Provide[Low, F, R]): ProvideLaws[Low, F, R] = {
    new ProvideLaws[Low, F, R] {
      override val instance = instance0
    }
  }
}
