package io.janstenpickle.trace4cats.http4s.server

import cats.data.Kleisli
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.janstenpickle.trace4cats.http4s.common.TraceContext
import io.janstenpickle.trace4cats.inject.Trace

object Instances {
  implicit val traceContextTrace: Trace[Kleisli[IO, TraceContext[IO], *]] =
    Trace.kleisliInstance[IO].lens[TraceContext[IO]](_.span, (c, span) => c.copy(span = span))
  implicit val runtime: IORuntime = IORuntime.global
}
