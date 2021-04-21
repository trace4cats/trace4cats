package io.janstenpickle.trace4cats.base.context.zio.laws

import cats.laws.discipline.SerializableTests
import cats.{~>, Eq}
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.base.context.laws.discipline.ProvideTests
import io.janstenpickle.trace4cats.base.context.zio.instances._
import org.scalacheck.{Arbitrary, Cogen}
import zio.interop.ZioSpecBase
import zio.{IO, RIO, Runtime, ZIO}

class ZIOTests extends ZioSpecBase {
  implicit def eqRIOLower[R, E](implicit ev: Eq[IO[E, R]]): Eq[ZIO[R, E, *] ~> IO[E, *]] =
    Eq.by((lower: ZIO[R, E, *] ~> IO[E, *]) => lower(RIO.environment[R]))

  implicit def cogenRIOLower[R: Cogen, E](implicit rts: Runtime[Any]): Cogen[ZIO[R, E, *] ~> IO[E, *]] =
    Cogen[R].contramap(k => rts.unsafeRun(k(RIO.environment[R])))

  checkAllAsync(
    "ZIO[String, Long, *]",
    { implicit tc =>
      type R = String
      type E = Long
      type F[x] = ZIO[R, E, x]
      type Low[x] = IO[E, x]

      implicit def ioArb[A: Arbitrary: Cogen]: Arbitrary[Low[A]] = arbitraryIO[E, A]
      implicit def zioArb[A: Arbitrary: Cogen]: Arbitrary[F[A]] = arbitraryZIO[R, E, A]

      ProvideTests[Low, F, R].provide[String, Int]
    }
  )

  checkAll(
    "Provide[IO[Long, *], ZIO[String, Long, *], String]",
    SerializableTests.serializable(Provide[IO[Long, *], ZIO[String, Long, *], String])
  )
}
