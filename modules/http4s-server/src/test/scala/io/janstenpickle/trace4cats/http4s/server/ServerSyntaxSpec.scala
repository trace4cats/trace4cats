package io.janstenpickle.trace4cats.http4s.server

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.Span
import syntax._

import scala.concurrent.ExecutionContext

class ServerSyntaxSpec
    extends BaseServerTracerSpec[IO, Kleisli[IO, Span[IO], *]](
      λ[IO ~> Id](_.unsafeRunSync()),
      λ[Kleisli[IO, Span[IO], *] ~> IO](ga => Span.noop[IO].use(ga.run)),
      (routes, filter, ep) => routes.inject(ep, requestFilter = filter),
      (app, filter, ep) => app.inject(ep, requestFilter = filter),
      IO.timer(ExecutionContext.global)
    )
