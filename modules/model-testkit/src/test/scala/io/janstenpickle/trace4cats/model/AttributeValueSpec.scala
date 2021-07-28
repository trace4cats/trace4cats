package io.janstenpickle.trace4cats.model

import cats.Eval
import cats.kernel.laws.discipline.SemigroupTests
import org.scalacheck.Arbitrary
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

class AttributeValueSpec extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with FunSuiteDiscipline {

  import ArbitraryAttributeValues._

  implicit def evalArb[A: Arbitrary]: Arbitrary[Eval[A]] = Arbitrary(Arbitrary.arbitrary[A].map(Eval.now))

  checkAll("AttributeValue semigroup", SemigroupTests[AttributeValue].semigroup)
}
