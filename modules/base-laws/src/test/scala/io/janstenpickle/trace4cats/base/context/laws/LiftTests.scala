package io.janstenpickle.trace4cats.base.context.laws

class LiftTests extends BaseSuite {
  checkAll("Lift[Option, Option]", discipline.LiftTests[Option, Option].lift[String, Int])
}
