package io.janstenpickle.trace4cats.http4s.server

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.base.context.Inject
import io.janstenpickle.trace4cats.http4s.common.{Request_, TraceContext}
import io.janstenpickle.trace4cats.http4s.server.syntax._

import scala.concurrent.ExecutionContext

class ServerCtxSyntaxInjectSpec
    extends BaseServerTracerSpec[IO, Kleisli[IO, TraceContext[IO], *]](
      9183,
      λ[IO ~> Id](_.unsafeRunSync()),
      λ[Kleisli[IO, TraceContext[IO], *] ~> IO](ga => TraceContext.empty[IO].flatMap(ga.run)),
      (routes, filter, ep) => {
        implicit val inject: Inject[IO, TraceContext[IO], Request_] =
          ServerInject.context(ep, requestFilter = filter, makeContext = TraceContext.make[IO])

        routes.tracedContext[TraceContext[IO]](_.span)
      },
      (app, filter, ep) => {
        implicit val inject: Inject[IO, TraceContext[IO], Request_] =
          ServerInject.context(ep, requestFilter = filter, makeContext = TraceContext.make[IO])

        app.tracedContext[TraceContext[IO]](_.span)
      },
      IO.timer(ExecutionContext.global)
    )
