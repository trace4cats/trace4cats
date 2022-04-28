package io.janstenpickle.trace4cats.base.context
package laws
package discipline

import cats.kernel.laws.discipline.catsLawsIsEqToProp
import cats.{~>, Eq}
import org.scalacheck.Prop.{forAll => ∀}
import org.scalacheck.{Arbitrary, Cogen}

trait UnliftTests[Low[_], F[_]] extends LiftTests[Low, F] {
  implicit val instance: Unlift[Low, F]

  override def laws: UnliftLaws[Low, F] = UnliftLaws[Low, F]

  def unlift[A: Arbitrary: Cogen, B](implicit
    ArbFA: Arbitrary[F[A]],
    ArbLowA: Arbitrary[Low[A]],
    ArbLowB: Arbitrary[Low[B]],
    CogenLower: Cogen[F ~> Low],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]]
  ): RuleSet = {
    new DefaultRuleSet(
      name = "unlift",
      parent = Some(lift[A, B]),
      "unlift idempotency" -> ∀(laws.unliftIdempotency[A] _),
      "withUnlift is askUnlift and flatMap" -> ∀(laws.withUnliftIsAskUnliftAndFlatMap[A] _)
    )
  }
}

object UnliftTests {
  def apply[Low[_], F[_]](implicit instance0: Unlift[Low, F]): UnliftTests[Low, F] = {
    new UnliftTests[Low, F] {
      val instance = instance0
    }
  }
}
