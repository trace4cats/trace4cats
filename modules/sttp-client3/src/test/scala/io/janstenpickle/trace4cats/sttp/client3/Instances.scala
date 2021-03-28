package io.janstenpickle.trace4cats.sttp.client3

import cats.data.Kleisli
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Local
import io.janstenpickle.trace4cats.http4s.common.TraceContext

object Instances {
  implicit val localSpan: Local[Kleisli[IO, TraceContext[IO], *], Span[IO]] =
    Local[Kleisli[IO, TraceContext[IO], *], TraceContext[IO]].focus(TraceContext.span[IO])

  implicit val runtime: IORuntime = IORuntime.global
}
