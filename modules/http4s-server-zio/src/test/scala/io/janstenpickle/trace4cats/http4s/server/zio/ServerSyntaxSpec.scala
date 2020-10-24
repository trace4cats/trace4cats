package io.janstenpickle.trace4cats.http4s.server.zio

import cats.effect.Timer
import cats.{~>, Id}
import io.janstenpickle.trace4cats.http4s.server.BaseServerTracerSpec
import io.janstenpickle.trace4cats.inject.zio._
import zio.{Runtime, Task}
import syntax._
import zio.interop.catz._

class ServerSyntaxSpec
    extends BaseServerTracerSpec[Task, ZIOTrace](
      9084,
      λ[Task ~> Id](fa => Runtime.default.unsafeRun(fa)),
      span => λ[ZIOTrace ~> Task](_.provide(span)),
      (routes, filter, ep) => routes.inject(ep, requestFilter = filter),
      (app, filter, ep) => app.inject(ep, requestFilter = filter),
      Timer[Task]
    )
