package io.janstenpickle.trace4cats.base.context

import cats.~>

trait Provide[Low[_], F[_], R] extends Local[F, R] with Unlift[Low, F] {
  def provide[A](fa: F[A])(r: R): Low[A]

  def provideK(r: R): F ~> Low = λ[F ~> Low](provide(_)(r))

  override def unlift: F[F ~> Low] = access(provideK)

  def kleislift[A](f: R => Low[A]): F[A] = accessM(f.andThen(lift))
}

object Provide {
  def apply[Low[_], F[_], R](implicit ev: Provide[Low, F, R]): Provide[Low, F, R] = ev
}
