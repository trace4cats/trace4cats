package io.janstenpickle.trace4cats.base.context.io

import cats.effect.{IO, IOLocal}
import io.janstenpickle.trace4cats.base.context.Provide

trait IOLocalContextInstances {
  def ioLocalProvide[Ctx](rootCtx: IOLocal[Ctx]): Provide[IO, IO, Ctx] =
    new IOLocalProvide(rootCtx)
}
