package io.janstenpickle.trace4cats.sttp.tapir

import cats.data.Kleisli
import cats.effect.IO
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Local
import io.janstenpickle.trace4cats.http4s.common.TraceContext

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import cats.effect.Temporal

object Instances {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()))
  implicit val timer: Temporal[IO] = IO.timer(ExecutionContext.global)

  implicit val localSpan: Local[Kleisli[IO, TraceContext[IO], *], Span[IO]] =
    Local[Kleisli[IO, TraceContext[IO], *], TraceContext[IO]].focus(TraceContext.span[IO])
}
