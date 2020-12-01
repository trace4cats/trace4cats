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
import cats.syntax.applicative._
import cats.syntax.functor._

// adapted from cats-mtl
trait LocalLaws[F[_], R] extends AskLaws[F, R] {
  override implicit def instance: Local[F, R]
  override implicit def F: Monad[F] = instance.F

  // external laws:
  def askReflectsLocal(f: R => R): IsEq[F[R]] =
    instance.local(instance.ask)(f) <-> instance.ask.map(f)

  def localPureIsPure[A](a: A, f: R => R): IsEq[F[A]] =
    instance.local(a.pure[F])(f) <-> a.pure[F]

  def localDistributesOverAp[A, B](fa: F[A], ff: F[A => B], f: R => R): IsEq[F[B]] =
    instance.local(F.ap(ff)(fa))(f) <-> F.ap(instance.local(ff)(f))(instance.local(fa)(f))

  // internal law:
  def scopeIsLocalConst[A](fa: F[A], e: R): IsEq[F[A]] =
    instance.scope(fa)(e) <-> instance.local(fa)(_ => e)

}

object LocalLaws {
  def apply[F[_], E](implicit instance0: Local[F, E]): LocalLaws[F, E] = {
    new LocalLaws[F, E] {
      val instance: Local[F, E] = instance0
    }
  }
}
