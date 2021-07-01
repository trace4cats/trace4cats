package io.janstenpickle.trace4cats.base.context

import cats.{~>, Monad}

trait Provide[Low[_], F[_], R] extends Local[F, R] with Unlift[Low, F] { self =>
  def provide[A](fa: F[A])(r: R): Low[A]

  def provideK(r: R): F ~> Low = new (F ~> Low) {
    override def apply[A](fa: F[A]): Low[A] = provide(fa)(r)
  }

  override def askUnlift: F[F ~> Low] = access(provideK)

  def kleislift[A](f: R => Low[A]): F[A] = accessF(f.andThen(lift))

  override def mapK[G[_]: Monad](fk: F ~> G): Ask[G, R] with Lift[Low, G] = Provide.mapK(self)(fk)

  override def imapK[G[_]: Monad](fk: F ~> G, gk: G ~> F): Provide[Low, G, R] =
    new Provide[Low, G, R] {
      def Low: Monad[Low] = self.Low
      def F: Monad[G] = implicitly
      def lift[A](la: Low[A]): G[A] = fk(self.lift(la))
      def ask[R1 >: R]: G[R1] = fk(self.ask[R1])
      def local[A](fa: G[A])(f: R => R): G[A] = fk(self.local(gk(fa))(f))
      def provide[A](fa: G[A])(r: R): Low[A] = self.provide(gk(fa))(r)
    }
}

object Provide {
  def apply[Low[_], F[_], R](implicit ev: Provide[Low, F, R]): Provide[Low, F, R] = ev

  private def mapK[F[_], G[_]: Monad, Low[_], R](self: Ask[F, R] with Lift[Low, F])(fk: F ~> G): Ask[G, R]
    with Lift[Low, G] =
    new Ask[G, R] with Lift[Low, G] {
      def Low: Monad[Low] = self.Low
      def F: Monad[G] = implicitly
      def lift[A](la: Low[A]): G[A] = fk(self.lift(la))
      def ask[R1 >: R]: G[R1] = fk(self.ask[R1])
      override def mapK[H[_]: Monad](gk: G ~> H): Ask[H, R] with Lift[Low, H] = Provide.mapK(this)(gk)
    }
}
