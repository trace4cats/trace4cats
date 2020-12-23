package io.janstenpickle.trace4cats.sttp.client3

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.ToHeaders
import io.janstenpickle.trace4cats.http4s.common.TraceContext
import io.janstenpickle.trace4cats.sttp.client3.Instances._
import io.janstenpickle.trace4cats.sttp.client3.syntax._

import scala.concurrent.ExecutionContext

class TracedSttpBackendCtxSpec
    extends BaseSttpBackendTracerSpec[IO, Kleisli[IO, TraceContext[IO], *], TraceContext[IO]](
      λ[IO ~> Id](_.unsafeRunSync()),
      TraceContext("bf2665b3-2201-466d-868d-8bd3ab151d79", _),
      _.liftTraceContext(spanLens = TraceContext.span[IO], headersGetter = TraceContext.headers[IO](ToHeaders.all))
    )
