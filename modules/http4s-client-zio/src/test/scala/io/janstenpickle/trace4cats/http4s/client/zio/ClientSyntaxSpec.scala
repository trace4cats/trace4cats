package io.janstenpickle.trace4cats.http4s.client.zio

import cats.effect.Timer
import cats.{~>, Id}
import io.janstenpickle.trace4cats.http4s.client.BaseClientTracerSpec
import io.janstenpickle.trace4cats.inject.zio._
import zio.{Runtime, Task}
import syntax._
import zio.interop.catz._

class ClientSyntaxSpec
    extends BaseClientTracerSpec[Task, ZIOTrace](
      λ[Task ~> Id](fa => Runtime.default.unsafeRun(fa)),
      span => λ[ZIOTrace ~> Task](_.provide(span)),
      _.liftTrace(),
      Timer[Task]
    )
