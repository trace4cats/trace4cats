// Adapted from cats-mtl
// Copyright (c) 2017 Cats-mtl Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package io.janstenpickle.trace4cats.base.context
package laws

import cats.Monad
import cats.laws.IsEq
import cats.laws.IsEqArrow
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._

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
