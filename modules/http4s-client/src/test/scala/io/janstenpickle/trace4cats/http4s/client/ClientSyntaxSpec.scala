package io.janstenpickle.trace4cats.http4s.client

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.http4s.client.syntax._

import scala.concurrent.ExecutionContext

class ClientSyntaxSpec
    extends BaseClientTracerSpec[IO, Kleisli[IO, Span[IO], *], Span[IO]](
      9084,
      λ[IO ~> Id](_.unsafeRunSync()),
      identity,
      _.liftTrace(),
      IO.timer(ExecutionContext.global)
    )
