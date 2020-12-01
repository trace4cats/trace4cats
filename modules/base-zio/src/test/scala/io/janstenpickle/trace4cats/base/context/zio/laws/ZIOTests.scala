package io.janstenpickle.trace4cats.base.context.zio.laws

import cats.laws.discipline.SerializableTests
import cats.{~>, Eq}
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.base.context.laws.discipline.ProvideTests
import io.janstenpickle.trace4cats.base.context.zio.instances._
import org.scalacheck.Cogen
import zio.{IO, RIO, Runtime, ZIO}

class ZIOTests extends catzSpecZIOBase {
  implicit def eqRIOLower[R, E](implicit ev: Eq[IO[E, R]]): Eq[ZIO[R, E, *] ~> IO[E, *]] =
    Eq.by((lower: ZIO[R, E, *] ~> IO[E, *]) => lower(RIO.environment[R]))

  implicit def cogenRIOLower[R: Cogen, E](implicit rts: Runtime[Any]): Cogen[ZIO[R, E, *] ~> IO[E, *]] =
    Cogen[R].contramap(k => rts.unsafeRun(k(RIO.environment[R])))

  checkAllAsync(
    "ZIO[String, Long, *]",
    implicit tc => ProvideTests[IO[Long, *], ZIO[String, Long, *], String].provide[String, Int]
  )

  checkAll(
    "Provide[IO[Long, *], ZIO[String, Long, *], String]",
    SerializableTests.serializable(Provide[IO[Long, *], ZIO[String, Long, *], String])
  )
}
