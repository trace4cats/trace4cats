package io.janstenpickle.trace4cats.sttp.backend.zio

import io.janstenpickle.trace4cats.sttp.backend.BaseBackendTracerSpec
import cats.effect.Timer
import cats.{~>, Id}
import io.janstenpickle.trace4cats.inject.zio._
import sttp.client.NothingT
import zio.{Runtime, Task}
import zio.interop.catz._

class TracedBackendSpec
    extends BaseBackendTracerSpec[Task, ZIOTrace](
      9094,
      λ[Task ~> Id](fa => Runtime.default.unsafeRun(fa)),
      span => λ[ZIOTrace ~> Task](_.provide(span)),
      TracedBackend[Nothing, NothingT](_),
      Timer[Task]
    )
