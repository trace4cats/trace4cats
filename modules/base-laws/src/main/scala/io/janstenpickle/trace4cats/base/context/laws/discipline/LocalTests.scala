// Adapted from cats-mtl
// Copyright (c) 2017 Cats-mtl Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package io.janstenpickle.trace4cats.base.context
package laws
package discipline

import cats.Eq
import cats.kernel.laws.discipline.catsLawsIsEqToProp
import org.scalacheck.Prop.{forAll => ∀}
import org.scalacheck.{Arbitrary, Cogen}

trait LocalTests[F[_], R] extends AskTests[F, R] {
  implicit val instance: Local[F, R]

  override def laws: LocalLaws[F, R] = LocalLaws[F, R]

  def local[A: Arbitrary, B](implicit
    ArbFA: Arbitrary[F[A]],
    ArbFAB: Arbitrary[F[A => B]],
    ArbRR: Arbitrary[R => R],
    ArbR: Arbitrary[R],
    CogenR: Cogen[R],
    EqFR: Eq[F[R]],
    EqFA: Eq[F[A]],
    EqFB: Eq[F[B]]
  ): RuleSet = {
    new DefaultRuleSet(
      name = "local",
      parent = Some(ask[A]),
      "ask reflects local" -> ∀(laws.askReflectsLocal _),
      "local pure is pure" -> ∀(laws.localPureIsPure[A] _),
      "local distributes over ap" -> ∀(laws.localDistributesOverAp[A, B] _),
      "scope is local const" -> ∀(laws.scopeIsLocalConst[A] _)
    )
  }
}

object LocalTests {
  def apply[F[_], R](implicit instance0: Local[F, R]): LocalTests[F, R] = {
    new LocalTests[F, R] {
      val instance = instance0
    }
  }
}
