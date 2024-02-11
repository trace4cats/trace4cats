// Adapted from cats-mtl
// Copyright (c) 2017 Cats-mtl Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package trace4cats.context.laws

import cats.Eq
import cats.laws.discipline.ExhaustiveCheck
import cats.syntax.{EqOps, EqSyntax}
import cats.tests.StrictCatsEquality
import org.scalacheck.{Arbitrary, Gen}
import org.scalactic.anyvals.{PosInt, PosZDouble, PosZInt}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.Configuration
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

abstract class BaseSuite
    extends AnyFunSuite
    with Matchers
    with Configuration
    with StrictCatsEquality
    with EqSyntax
    with FunSuiteDiscipline {

  implicit def exhaustiveCheckForArbitrary[A: Arbitrary]: ExhaustiveCheck[A] =
    ExhaustiveCheck.instance(Gen.resize(30, Arbitrary.arbitrary[List[A]]).sample.get)

  // disable Eq syntax (by making `catsSyntaxEq` not implicit), since it collides
  // with scalactic's equality
  override def catsSyntaxEq[@scala.specialized(Int, Long, Float, Double) A: Eq](a: A): EqOps[A] =
    new EqOps[A](a)

  override implicit val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(
      minSuccessful = PosInt(100),
      maxDiscardedFactor = PosZDouble(5.0),
      minSize = PosZInt(0),
      sizeRange = PosZInt(10),
      workers = PosInt(2)
    )
}
