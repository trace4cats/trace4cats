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

import cats.Monad
import cats.laws.IsEq
import cats.laws.IsEqArrow
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._

// adapted from cats-mtl
trait AskLaws[F[_], R] {
  implicit def instance: Ask[F, R]

  implicit def F: Monad[F] = instance.F

  // external law:
  def askAddsNoEffects[A](fa: F[A]): IsEq[F[A]] =
    instance.ask *> fa <-> fa

  // internal laws:
  def accessIsAskAndMap[A](f: R => A): IsEq[F[A]] =
    instance.ask.map(f) <-> instance.access(f)

  def accessFIsAskAndFlatMap[A](f: R => F[A]): IsEq[F[A]] =
    instance.ask.flatMap(f) <-> instance.accessF(f)

}

object AskLaws {
  def apply[F[_], E](implicit instance0: Ask[F, E]): AskLaws[F, E] = {
    new AskLaws[F, E] {
      override val instance = instance0
    }
  }
}
