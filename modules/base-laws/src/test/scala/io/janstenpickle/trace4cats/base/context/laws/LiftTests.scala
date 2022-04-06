package io.janstenpickle.trace4cats.base.context.laws

import io.janstenpickle.trace4cats.base.context.laws.discipline

class LiftTests extends BaseSuite {
  checkAll("Lift[Option, Option]", discipline.LiftTests[Option, Option].lift[String, Int])
}
