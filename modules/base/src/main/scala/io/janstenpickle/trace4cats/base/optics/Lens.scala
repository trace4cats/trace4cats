package io.janstenpickle.trace4cats.base.optics

import scala.Function.const

trait Lens[S, A] extends Getter[S, A] { self =>
  def get(s: S): A

  def set(a: A): S => S

  def modify(f: A => A): S => S = s => set(f(get(s)))(s)

  def composeLens[T](g: Lens[T, S]): Lens[T, A] = new Lens[T, A] {
    def get(t: T): A = self.get(g.get(t))
    def set(a: A): T => T = g.modify(self.set(a))
  }
}

object Lens {
  def apply[S, A](_get: S => A)(_set: A => S => S): Lens[S, A] = new Lens[S, A] {
    def get(s: S): A = _get(s)
    def set(a: A): S => S = _set(a)
  }

  implicit def id[A]: Lens[A, A] = apply(identity[A])(const)
}
