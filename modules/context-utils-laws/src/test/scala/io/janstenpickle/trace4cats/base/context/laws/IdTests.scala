package io.janstenpickle.trace4cats.base.context.laws

import cats.~>
import cats.laws.discipline.SerializableTests
import io.janstenpickle.trace4cats.base.context.Unlift
import io.janstenpickle.trace4cats.base.context.laws.discipline.UnliftTests
import org.scalacheck.Cogen

class IdTests extends BaseSuite {
  implicit val cogenOption2Option: Cogen[Option ~> Option] =
    Cogen(k => k(Some(0)).size.toLong)

  checkAll("Option <~> Option", UnliftTests[Option, Option].unlift[String, Int])

  checkAll("Unlift[Option, Option]", SerializableTests.serializable(Unlift[Option, Option]))
}
