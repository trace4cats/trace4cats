package io.janstenpickle.trace4cats.base.optics

trait Getter[S, A] { self =>
  def get(s: S): A

  def composeGetter[T](other: Getter[T, S]): Getter[T, A] = s => self.get(other.get(s))
}

object Getter {
  def apply[S, A](_get: S => A): Getter[S, A] = _get(_)
  def id[A]: Getter[A, A] = identity[A]
}
