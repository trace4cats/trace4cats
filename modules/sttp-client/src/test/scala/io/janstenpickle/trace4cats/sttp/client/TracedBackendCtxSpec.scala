package io.janstenpickle.trace4cats.sttp.client

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.ToHeaders
import io.janstenpickle.trace4cats.http4s.common.TraceContext
import io.janstenpickle.trace4cats.sttp.client.Instances._
import io.janstenpickle.trace4cats.sttp.client.syntax._

class TracedBackendCtxSpec
    extends BaseBackendTracerSpec[IO, Kleisli[IO, TraceContext[IO], *], TraceContext[IO]](
      λ[IO ~> Id](_.unsafeRunSync()),
      TraceContext("bf2665b3-2201-466d-868d-8bd3ab151d79", _),
      _.liftTraceContext(spanLens = TraceContext.span[IO], headersGetter = TraceContext.headers[IO](ToHeaders.all))
    )
