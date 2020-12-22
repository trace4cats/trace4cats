package io.janstenpickle.trace4cats.sttp.client3

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.sttp.client3.Instances._
import io.janstenpickle.trace4cats.sttp.client3.syntax._

import scala.concurrent.ExecutionContext

class TracedSttpBackendSpec
    extends BaseSttpBackendTracerSpec[IO, Kleisli[IO, Span[IO], *], Span[IO]](
      λ[IO ~> Id](_.unsafeRunSync()),
      identity,
      _.liftTrace(),
      IO.timer(ExecutionContext.global)
    )
