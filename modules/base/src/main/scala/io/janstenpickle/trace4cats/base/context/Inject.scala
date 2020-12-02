package io.janstenpickle.trace4cats.base.context

trait Inject[F[_], R, R0] { self =>
  def apply[A](r0: R0)(f: R => F[A]): F[A]
  def contramap[R1](f: R1 => R0): Inject[F, R, R1] = new Inject[F, R, R1] {
    override def apply[A](r1: R1)(g: R => F[A]): F[A] = self.apply(f(r1))(g)
  }
}

object Inject {
  def apply[F[_], R, R0](implicit ev: Inject[F, R, R0]): Inject[F, R, R0] = ev
}
