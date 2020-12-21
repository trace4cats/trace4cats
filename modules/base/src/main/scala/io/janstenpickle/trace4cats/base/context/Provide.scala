package io.janstenpickle.trace4cats.base.context

import cats.{~>, Monad}

trait Provide[Low[_], F[_], R] extends Local[F, R] with Unlift[Low, F] {
  def provide[A](fa: F[A])(r: R): Low[A]

  def provideK(r: R): F ~> Low = λ[F ~> Low](provide(_)(r))

  override def askUnlift: F[F ~> Low] = access(provideK)

  def kleislift[A](f: R => Low[A]): F[A] = accessF(f.andThen(lift))

  def imapK[G[_]: Monad](fk: F ~> G, gk: G ~> F): Provide[Low, G, R] = Provide.imapK(this, fk, gk)
}

object Provide {
  def apply[Low[_], F[_], R](implicit ev: Provide[Low, F, R]): Provide[Low, F, R] = ev

  private def imapK[Low[_]: Monad, F[_], G[_]: Monad, R](
    self: Provide[Low, F, R],
    fk: F ~> G,
    gk: G ~> F
  ): Provide[Low, G, R] =
    new Provide[Low, G, R] {
      override def provide[A](fa: G[A])(r: R): Low[A] = self.provide(gk(fa))(r)

      override def local[A](fa: G[A])(f: R => R): G[A] = fk(self.local(gk(fa))(f))

      override def Low: Monad[Low] = implicitly

      override def F: Monad[G] = implicitly

      override def lift[A](la: Low[A]): G[A] = fk(self.lift(la))

      override def ask[R1 >: R]: G[R1] = fk(self.ask[R1])
    }
}
