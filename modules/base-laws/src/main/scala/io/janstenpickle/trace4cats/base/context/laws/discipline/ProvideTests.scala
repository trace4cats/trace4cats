package io.janstenpickle.trace4cats.base.context
package laws
package discipline

import cats.kernel.laws.discipline.catsLawsIsEqToProp
import cats.{~>, Eq}
import org.scalacheck.Prop.{forAll => ∀}
import org.scalacheck.{Arbitrary, Cogen, Prop}

trait ProvideTests[Low[_], F[_], R] extends LocalTests[F, R] with UnliftTests[Low, F] {
  implicit val instance: Provide[Low, F, R]

  override def laws: ProvideLaws[Low, F, R] = ProvideLaws[Low, F, R]

  def provide[A: Arbitrary: Cogen, B](implicit
    ArbFA: Arbitrary[F[A]],
    ArbFAB: Arbitrary[F[A => B]],
    ArbRR: Arbitrary[R => R],
    ArbR: Arbitrary[R],
    ArbLowA: Arbitrary[Low[A]],
    ArbLowB: Arbitrary[Low[B]],
    CogenLower: Cogen[F ~> Low],
    CogenR: Cogen[R],
    EqFR: Eq[F[R]],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]],
    EqLowA: Eq[Low[A]]
  ): RuleSet = {
    new RuleSet {
      def name: String = "provide"
      def bases: Seq[(String, RuleSet)] = Nil
      def parents: Seq[RuleSet] = Seq(local[A, B], unlift[A, B])
      def props: Seq[(String, Prop)] = Seq(
        "askUnlift is access and provideK" -> ∀(laws.askUnliftIsAccessProvideK[A] _),
        "kleslift is lift and accessF" -> ∀(laws.kleisliftIsLiftAndAccessF[A] _),
        "kleslift and provide is apply" -> ∀(laws.klesliftAndProvideIsApply[A] _)
      )
    }
  }
}

object ProvideTests {
  def apply[Low[_], F[_], R](implicit instance0: Provide[Low, F, R]): ProvideTests[Low, F, R] = {
    new ProvideTests[Low, F, R] {
      val instance = instance0
    }
  }
}
