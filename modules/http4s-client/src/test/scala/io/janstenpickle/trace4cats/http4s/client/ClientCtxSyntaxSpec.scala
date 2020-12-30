package io.janstenpickle.trace4cats.http4s.client

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.ToHeaders
import io.janstenpickle.trace4cats.http4s.client.syntax._
import io.janstenpickle.trace4cats.http4s.client.Instances._
import io.janstenpickle.trace4cats.http4s.common.TraceContext

class ClientCtxSyntaxSpec
    extends BaseClientTracerSpec[IO, Kleisli[IO, TraceContext[IO], *], TraceContext[IO]](
      λ[IO ~> Id](_.unsafeRunSync()),
      TraceContext("3d86cad5-d321-448f-a758-d28714fc1045", _),
      _.liftTraceContext(spanLens = TraceContext.span[IO], headersGetter = TraceContext.headers[IO](ToHeaders.all))
    )
