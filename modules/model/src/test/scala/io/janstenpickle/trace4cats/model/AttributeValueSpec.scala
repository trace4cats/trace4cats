package io.janstenpickle.trace4cats.model

import cats.kernel.laws.discipline.SemigroupTests
import org.scalacheck.ScalacheckShapeless
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

class AttributeValueSpec
    extends AnyFunSuite
    with ScalaCheckDrivenPropertyChecks
    with FunSuiteDiscipline
    with ScalacheckShapeless {

  checkAll("AttributeValue semigroup", SemigroupTests[AttributeValue].semigroup)
}
