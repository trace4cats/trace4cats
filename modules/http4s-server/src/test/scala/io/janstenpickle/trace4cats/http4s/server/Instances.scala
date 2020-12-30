package io.janstenpickle.trace4cats.http4s.server

import java.util.concurrent.Executors
import cats.data.Kleisli
import cats.effect.{ContextShift, IO, Timer}
import io.janstenpickle.trace4cats.http4s.common.TraceContext
import io.janstenpickle.trace4cats.inject.Trace

import scala.concurrent.ExecutionContext

object Instances {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()))
  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  implicit val traceContextTrace: Trace[Kleisli[IO, TraceContext[IO], *]] =
    Trace.kleisliInstance[IO].lens[TraceContext[IO]](_.span, (c, span) => c.copy(span = span))
}
