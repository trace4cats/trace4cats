package io.janstenpickle.trace4cats.base.context

import io.janstenpickle.trace4cats.base.context.Env.{Sub1, Sub2}
import io.janstenpickle.trace4cats.base.optics.{Getter, Lens}

case class Env(sub1: Sub1, sub2: Sub2)

object Env {
  type Sub1
  type Sub2

  val sub1: Getter[Env, Sub1] = _.sub1
  val sub2: Lens[Env, Sub2] = Lens[Env, Sub2](_.sub2)(x => _.copy(sub2 = x))
}
