package io.janstenpickle.trace4cats.base.context.laws

import cats.~>
import io.janstenpickle.trace4cats.base.context.laws.discipline
import org.scalacheck.Cogen

class UnliftTests extends BaseSuite {
  implicit val cogenOption2Option: Cogen[Option ~> Option] =
    Cogen(_ => 0L)

  checkAll("Unlift[Option, Option]", discipline.UnliftTests[Option, Option].unlift[String, Int])
}
