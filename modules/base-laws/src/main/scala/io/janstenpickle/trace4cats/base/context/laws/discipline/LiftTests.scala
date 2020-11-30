package io.janstenpickle.trace4cats.base.context
package laws
package discipline

import cats.Eq
import cats.kernel.laws.discipline.catsLawsIsEqToProp
import org.scalacheck.{Arbitrary, Cogen}
import org.scalacheck.Prop.{forAll => ∀}
import org.typelevel.discipline.Laws

trait LiftTests[Low[_], F[_]] extends Laws {
  implicit val instance: Lift[Low, F]

  def laws: LiftLaws[Low, F] = LiftLaws[Low, F]

  def lift[A: Arbitrary: Cogen, B](implicit
    ArbLowA: Arbitrary[Low[A]],
    ArbLowB: Arbitrary[Low[B]],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]]
  ): RuleSet = {
    new DefaultRuleSet(
      name = "lift",
      parent = None,
      "lift identity" -> ∀(laws.liftIdentity[A] _),
      "lift composition" -> ∀(laws.liftComposition[A, B] _)
    )
  }
}

object LiftTests {
  def apply[Low[_], F[_]](implicit instance0: Lift[Low, F]): LiftTests[Low, F] = {
    new LiftTests[Low, F] {
      val instance = instance0
    }
  }
}
