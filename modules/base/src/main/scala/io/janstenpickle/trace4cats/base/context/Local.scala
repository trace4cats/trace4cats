package io.janstenpickle.trace4cats.base.context

import cats.{~>, Monad}
import io.janstenpickle.trace4cats.base.optics.Lens

trait Local[F[_], R] extends Ask[F, R] { self =>
  def local[A](fa: F[A])(f: R => R): F[A]
  def localK(f: R => R): F ~> F = λ[F ~> F](local(_)(f))

  def scope[A](fa: F[A])(r: R): F[A] = local(fa)(_ => r)
  def scopeK(r: R): F ~> F = λ[F ~> F](scope(_)(r))

  def focus[R1](lens: Lens[R, R1]): Local[F, R1] = new Local[F, R1] {
    def F: Monad[F] = self.F
    def local[A](fa: F[A])(f: R1 => R1): F[A] = self.local(fa)(r => lens.set(f(lens.get(r)))(r))
    def ask[R2 >: R1]: F[R2] = self.access(r => lens.get(r))
  }
}

object Local {
  def apply[F[_], R](implicit ev: Local[F, R]): Local[F, R] = ev
}
