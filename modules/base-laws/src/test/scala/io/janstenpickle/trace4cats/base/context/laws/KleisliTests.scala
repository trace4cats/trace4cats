// Adapted from cats-mtl
// Copyright (c) 2017 Cats-mtl Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package io.janstenpickle.trace4cats.base.context.laws

import cats.data._
import cats.laws.discipline.SerializableTests
import cats.laws.discipline.arbitrary._
import cats.laws.discipline.eq._
import cats.{~>, Applicative, Eq}
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.base.context.laws.discipline._
import org.scalacheck._

class KleisliTests extends BaseSuite {
  implicit def eqKleisli[F[_], A, B](implicit arb: Arbitrary[A], ev: Eq[F[B]]): Eq[Kleisli[F, A, B]] =
    Eq.by((_: Kleisli[F, A, B]).run)

  implicit def eqKleisliLower[F[_]: Applicative, A](implicit ev: Eq[F[A]]): Eq[Kleisli[F, A, *] ~> F] =
    Eq.by((lower: Kleisli[F, A, *] ~> F) => lower(Kleisli.ask[F, A]))

  implicit def cogenKleisliOptionLower[R: Cogen]: Cogen[Kleisli[Option, R, *] ~> Option] =
    Cogen[R].contramap(k => k(Kleisli((r: R) => Some(r))).get)

  checkAll("Kleisli[Option, String, *]", ProvideTests[Option, Kleisli[Option, String, *], String].provide[String, Int])

  checkAll(
    "Provide[Option, Kleisli[Option, String, *], String]",
    SerializableTests.serializable(Provide[Option, Kleisli[Option, String, *], String])
  )
}
