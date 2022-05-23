package trace4cats.context.laws

import cats.laws.discipline.SerializableTests
import cats.~>
import org.scalacheck.Cogen
import trace4cats.context.Unlift
import trace4catstests.context.laws.discipline.UnliftTests

class IdTests extends BaseSuite {
  implicit val cogenOption2Option: Cogen[Option ~> Option] =
    Cogen(k => k(Some(0)).size.toLong)

  checkAll("Option <~> Option", UnliftTests[Option, Option].unlift[String, Int])

  checkAll("Unlift[Option, Option]", SerializableTests.serializable(Unlift[Option, Option]))
}
