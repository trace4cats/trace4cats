package io.janstenpickle.trace4cats.sttp.client3

import cats.data.Kleisli
import cats.effect.{ContextShift, IO}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Local
import io.janstenpickle.trace4cats.http4s.common.TraceContext

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

object Instances {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()))

  implicit val localSpan: Local[Kleisli[IO, TraceContext[IO], *], Span[IO]] =
    Local[Kleisli[IO, TraceContext[IO], *], TraceContext[IO]].focus(TraceContext.span[IO])
}
