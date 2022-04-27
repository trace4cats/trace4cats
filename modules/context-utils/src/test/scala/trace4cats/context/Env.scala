package trace4cats.context

import trace4cats.context.Env._
import trace4cats.optics.{Getter, Lens}

case class Env(sub1: Sub1, sub2: Sub2)

object Env {
  type Sub1
  type Sub2

  val sub1: Getter[Env, Sub1] = _.sub1
  val sub2: Lens[Env, Sub2] = Lens[Env, Sub2](_.sub2)(x => _.copy(sub2 = x))
}
