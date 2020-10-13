package io.janstenpickle.trace4cats.http4s.server

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.Span
import syntax._

import scala.concurrent.ExecutionContext

class ServerSyntaxSpec
    extends BaseServerTracerSpec[IO, Kleisli[IO, Span[IO], *]](
      8082,
      λ[IO ~> Id](_.unsafeRunSync()),
      span => λ[Kleisli[IO, Span[IO], *] ~> IO](_(span)),
      _.inject(_),
      _.inject(_),
      IO.timer(ExecutionContext.global)
    )
