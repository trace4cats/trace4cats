package io.janstenpickle.trace4cats.base.context

import cats.{~>, Monad}
import io.janstenpickle.trace4cats.base.optics.Lens

trait Local[F[_], R] extends Ask[F, R] { self =>
  def local[A](fa: F[A])(f: R => R): F[A]
  def localK(f: R => R): F ~> F = new F ~> F {
    override def apply[A](fa: F[A]): F[A] = local(fa)(f)
  }

  def scope[A](fa: F[A])(r: R): F[A] = local(fa)(_ => r)
  def scopeK(r: R): F ~> F = new F ~> F {
    override def apply[A](fa: F[A]): F[A] = scope(fa)(r)
  }

  def focus[R1](lens: Lens[R, R1]): Local[F, R1] = new Local[F, R1] {
    val F: Monad[F] = self.F
    def ask[R2 >: R1]: F[R2] = self.access(lens.get)
    def local[A](fa: F[A])(f: R1 => R1): F[A] = self.local(fa)(r => lens.set(f(lens.get(r)))(r))
  }

  def imapK[G[_]: Monad](fk: F ~> G, gk: G ~> F): Local[G, R] = new Local[G, R] {
    val F: Monad[G] = implicitly
    def ask[R1 >: R]: G[R1] = fk(self.ask[R1])
    def local[A](fa: G[A])(f: R => R): G[A] = fk(self.local(gk(fa))(f))
  }
}

object Local {
  def apply[F[_], R](implicit ev: Local[F, R]): Local[F, R] = ev
}
