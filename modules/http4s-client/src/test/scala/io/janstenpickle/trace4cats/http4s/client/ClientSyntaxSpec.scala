package io.janstenpickle.trace4cats.http4s.client

import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.http4s.client.syntax._
import io.janstenpickle.trace4cats.inject.Spanned

import scala.concurrent.ExecutionContext

class ClientSyntaxSpec
    extends BaseClientTracerSpec[IO, Spanned[IO, *], Span[IO]](
      9084,
      λ[IO ~> Id](_.unsafeRunSync()),
      identity,
      _.liftTrace(),
      IO.timer(ExecutionContext.global)
    )
