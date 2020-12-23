package io.janstenpickle.trace4cats.sttp.tapir

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.http4s.common.TraceContext
import io.janstenpickle.trace4cats.sttp.tapir.Instances._

class TracedServerEndpointCtxSpec
    extends BaseServerEndpointTracerSpec[IO](
      λ[IO ~> Id](_.unsafeRunSync()),
      new Endpoints[IO, Kleisli[IO, TraceContext[IO], *]].tracedContextEndpoints(_),
      checkMkContextErrors = true
    )
