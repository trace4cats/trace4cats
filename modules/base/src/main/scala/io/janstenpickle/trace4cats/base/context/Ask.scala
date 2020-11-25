package io.janstenpickle.trace4cats.base.context

import cats.Monad
import io.janstenpickle.trace4cats.base.optics.Getter

trait Ask[F[_], R] extends ContextRoot { self =>
  protected def F: Monad[F]

  def ask[R1 >: R]: F[R1]

  def access[R1](f: R => R1): F[R1] = F.map(ask)(f)
  def accessM[R1](f: R => F[R1]): F[R1] = F.flatMap(ask)(f)

  def zoom[R1](g: Getter[R, R1]): Ask[F, R1] = new Ask[F, R1] {
    def F: Monad[F] = self.F
    def ask[R2 >: R1]: F[R2] = self.access(r => g.get(r))
  }
}

object Ask {
  def apply[F[_], R](implicit ev: Ask[F, R]): ev.type = ev
}
