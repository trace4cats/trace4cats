package io.janstenpickle.trace4cats.sttp.tapir

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.sttp.tapir.Instances._

class TracedServerEndpointSpec
    extends BaseServerEndpointTracerSpec[IO](
      λ[IO ~> Id](_.unsafeRunSync()),
      new Endpoints[IO, Kleisli[IO, Span[IO], *]].tracedEndpoints(_),
      checkMkContextErrors = false
    )
