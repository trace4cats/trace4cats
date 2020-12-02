package io.janstenpickle.trace4cats.http4s.server

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Inject
import io.janstenpickle.trace4cats.http4s.common.Request_
import io.janstenpickle.trace4cats.http4s.server.syntax._

import scala.concurrent.ExecutionContext

class ServerSyntaxInjectSpec
    extends BaseServerTracerSpec[IO, Kleisli[IO, Span[IO], *]](
      9182,
      λ[IO ~> Id](_.unsafeRunSync()),
      λ[Kleisli[IO, Span[IO], *] ~> IO](ga => Span.noop[IO].use(ga.run)),
      (routes, filter, ep) => {
        implicit val inject: Inject[IO, Span[IO], Request_] = ServerInject.span(ep, requestFilter = filter)

        routes.traced()
      },
      (app, filter, ep) => {
        implicit val inject: Inject[IO, Span[IO], Request_] = ServerInject.span(ep, requestFilter = filter)

        app.traced()
      },
      IO.timer(ExecutionContext.global)
    )
