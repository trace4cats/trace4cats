package trace4cats.context.iolocal

import cats.effect.{IO, IOLocal}
import trace4cats.context.Provide

trait IOLocalContextInstances {
  def ioLocalProvide[Ctx](rootCtx: IOLocal[Ctx]): Provide[IO, IO, Ctx] =
    new IOLocalProvide(rootCtx)
}
