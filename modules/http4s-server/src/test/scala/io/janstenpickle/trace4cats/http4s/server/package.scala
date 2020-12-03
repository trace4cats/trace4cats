package io.janstenpickle.trace4cats.http4s

import java.util.concurrent.Executors

import cats.data.Kleisli
import cats.effect.{ContextShift, IO}
import io.janstenpickle.trace4cats.http4s.common.TraceContext
import io.janstenpickle.trace4cats.inject.Trace

import scala.concurrent.ExecutionContext

package object server {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()))

  implicit val traceContextTrace: Trace[Kleisli[IO, TraceContext[IO], *]] =
    Trace.kleisliInstance[IO].lens[TraceContext[IO]](_.span, (c, span) => c.copy(span = span))
}
