/*
 * Copyright 2020 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.janstenpickle.trace4cats.base.context
package laws
package discipline

import cats.Eq
import cats.kernel.laws.discipline.catsLawsIsEqToProp
import org.scalacheck.Prop.{forAll => ∀}
import org.scalacheck.{Arbitrary, Cogen}

// adapted from cats-mtl
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
