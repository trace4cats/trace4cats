// Adapted from cats-mtl
// Copyright (c) 2017 Cats-mtl Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package io.janstenpickle.trace4cats.base.context
package laws
package discipline

import cats.Eq
import org.scalacheck.Prop.{forAll => ∀}
import org.scalacheck.{Arbitrary, Cogen}
import org.typelevel.discipline.Laws
import cats.kernel.laws.discipline.catsLawsIsEqToProp

trait AskTests[F[_], R] extends Laws {
  implicit val instance: Ask[F, R]

  def laws: AskLaws[F, R] = AskLaws[F, R]

  def ask[A: Arbitrary](implicit ArbFA: Arbitrary[F[A]], CogenR: Cogen[R], EqFA: Eq[F[A]]): RuleSet = {
    new DefaultRuleSet(
      name = "ask",
      parent = None,
      "ask adds no effects" -> ∀(laws.askAddsNoEffects[A] _),
      "access is ask and map" -> ∀(laws.accessIsAskAndMap[A] _),
      "accessF is ask and flatMap" -> ∀(laws.accessFIsAskAndFlatMap[A] _),
    )
  }

}

object AskTests {
  def apply[F[_], R](implicit instance0: Ask[F, R]): AskTests[F, R] = {
    new AskTests[F, R] {
      val instance = instance0
    }
  }
}
