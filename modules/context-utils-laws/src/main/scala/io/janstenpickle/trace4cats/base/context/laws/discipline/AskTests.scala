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
import org.scalacheck.Prop.{forAll => ∀}
import org.scalacheck.{Arbitrary, Cogen}
import org.typelevel.discipline.Laws
import cats.kernel.laws.discipline.catsLawsIsEqToProp

// adapted from cats-mtl
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
