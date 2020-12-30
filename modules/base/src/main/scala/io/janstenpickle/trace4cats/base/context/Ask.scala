package io.janstenpickle.trace4cats.base.context

import cats.{~>, Monad}
import io.janstenpickle.trace4cats.base.optics.Getter

trait Ask[F[_], R] extends ContextRoot { self =>
  def F: Monad[F]

  def ask[R1 >: R]: F[R1]

  def access[A](f: R => A): F[A] = F.map(ask)(f)
  def accessF[A](f: R => F[A]): F[A] = F.flatMap(ask)(f)

  def zoom[R1](g: Getter[R, R1]): Ask[F, R1] = new Ask[F, R1] {
    def F: Monad[F] = self.F
    def ask[R2 >: R1]: F[R2] = self.access(g.get)
  }

  def mapK[G[_]: Monad](fk: F ~> G): Ask[G, R] = new Ask[G, R] {
    def F: Monad[G] = implicitly
    def ask[R1 >: R]: G[R1] = fk(self.ask[R1])
  }
}

object Ask {
  def apply[F[_], R](implicit ev: Ask[F, R]): Ask[F, R] = ev
}
