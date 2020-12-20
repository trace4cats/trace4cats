package io.janstenpickle.trace4cats.http4s.server

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.http4s.common.TraceContext
import io.janstenpickle.trace4cats.http4s.server.syntax._

import scala.concurrent.ExecutionContext

class ResourceKleisliServerCtxSyntaxSpec
    extends BaseServerTracerSpec[IO, Kleisli[IO, TraceContext[IO], *]](
      λ[IO ~> Id](_.unsafeRunSync()),
      λ[Kleisli[IO, TraceContext[IO], *] ~> IO](ga => TraceContext.empty[IO].flatMap(ga.run)),
      (routes, filter, ep) =>
        routes.tracedContext(
          Http4sResourceKleislis
            .fromHeadersContext(TraceContext.make[IO], requestFilter = filter)(ep.toKleisli)
        ),
      (app, filter, ep) =>
        app.tracedContext(
          Http4sResourceKleislis
            .fromHeadersContext(TraceContext.make[IO], requestFilter = filter)(ep.toKleisli)
        ),
      IO.timer(ExecutionContext.global)
    )
