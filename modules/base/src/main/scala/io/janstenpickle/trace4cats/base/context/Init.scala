package io.janstenpickle.trace4cats.base.context
import cats.Monad

trait Init[Low[_], F[_], R, R0] extends Provide[Low, F, R] {
  def P: Provide[Low, F, R]

  def init[A](fa: F[A])(r0: R0): Low[A]

  override def provide[A](fa: F[A])(r: R): Low[A] = P.provide(fa)(r)

  override def ask[R1 >: R]: F[R1] = P.ask

  override def lift[A](la: Low[A]): F[A] = P.lift(la)

  override def local[A](fa: F[A])(f: R => R): F[A] = P.local(fa)(f)
}

object Init {
  def apply[Low[_], F[_], R, R1](implicit init: Init[Low, F, R, R1]): Init[Low, F, R, R1] = implicitly

  implicit def fromInjectProvide[Low[_]: Monad, F[_]: Monad, R, R0](implicit
    I: Inject[Low, R, R0],
    pv: Provide[Low, F, R]
  ): Init[Low, F, R, R0] =
    new Init[Low, F, R, R0] {
      override def P: Provide[Low, F, R] = pv

      override def init[A](fa: F[A])(r0: R0): Low[A] = I(r0)(P.provide(fa))

      override def Low: Monad[Low] = implicitly

      override def F: Monad[F] = implicitly
    }
}
