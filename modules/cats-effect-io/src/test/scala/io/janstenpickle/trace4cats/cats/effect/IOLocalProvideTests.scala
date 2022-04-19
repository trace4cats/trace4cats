// Adapted from cats-mtl
// Copyright (c) 2017 Cats-mtl Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package io.janstenpickle.trace4cats.cats.effect

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, IOLocal}
import cats.{~>, Eq}
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.base.context.laws.BaseSuite
import io.janstenpickle.trace4cats.base.context.laws.discipline.ProvideTests
import org.scalacheck.{Arbitrary, Cogen}

class IOLocalProvideTests extends BaseSuite {

  implicit val cogenIO2IO: Cogen[IO ~> IO] =
    Cogen(k => k(IO.pure(0)).hashCode().toLong)

  implicit def eqIO[A: Eq]: Eq[IO[A]] = Eq.by(_.unsafeRunSync())

  // TODO: This signature is non-sensical, the implementation as well (test passes though)
  implicit val eqIO2IO: Eq[IO[IO ~> IO]] = Eq.by(_ => false)

  implicit def arbIO[A: Arbitrary]: Arbitrary[IO[A]] = Arbitrary(Arbitrary.arbitrary[A].map(IO.pure))

  implicit val ioLocal: IOLocal[String] = IOLocal("").unsafeRunSync()
  implicit val ioLocalProvide: Provide[IO, IO, String] = new ProvideForIOLocal(ioLocal)

  checkAll("IO <~> IO", ProvideTests[IO, IO, String].provide[String, Int])

}
