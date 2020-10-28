package io.janstenpickle.trace4cats.sttp.backend

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.sttp.backend.Instances._
import sttp.client.NothingT

import scala.concurrent.ExecutionContext

class TracedBackendSpec
    extends BaseBackendTracerSpec[IO, Kleisli[IO, Span[IO], *]](
      9093,
      λ[IO ~> Id](_.unsafeRunSync()),
      TracedBackend[IO, Kleisli[IO, Span[IO], *], Nothing, NothingT](_),
      IO.timer(ExecutionContext.global)
    )
