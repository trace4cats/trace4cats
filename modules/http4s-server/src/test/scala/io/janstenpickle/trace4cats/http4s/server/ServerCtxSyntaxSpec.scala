package io.janstenpickle.trace4cats.http4s.server

import cats.data.Kleisli
import cats.effect.IO
import cats.{~>, Id}
import io.janstenpickle.trace4cats.http4s.common.TraceContext
import io.janstenpickle.trace4cats.http4s.server.syntax._
import io.janstenpickle.trace4cats.http4s.server.Instances._

class ServerCtxSyntaxSpec
    extends BaseServerTracerSpec[IO, Kleisli[IO, TraceContext[IO], *]](
      λ[IO ~> Id](_.unsafeRunSync()),
      λ[Kleisli[IO, TraceContext[IO], *] ~> IO](ga => TraceContext.empty[IO].flatMap(ga.run)),
      (routes, filter, ep) => routes.injectContext(ep, makeContext = TraceContext.make[IO], requestFilter = filter),
      (app, filter, ep) => app.injectContext(ep, makeContext = TraceContext.make[IO], requestFilter = filter),
    )
