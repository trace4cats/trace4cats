package io.janstenpickle.trace4cats.cats.effect

import cats.Monad
import cats.effect.{IO, IOLocal, Resource}
import io.janstenpickle.trace4cats.base.context.Local

private[trace4cats] class LocalForIOLocal[Ctx](rootCtx: IOLocal[Ctx]) extends Local[IO, Ctx] {
  val F: Monad[IO] = Monad[IO]
  def ask[R1 >: Ctx]: IO[R1] = rootCtx.get
  def local[A](fa: IO[A])(f: Ctx => Ctx): IO[A] =
    rootCtx.get.flatMap { parent =>
      Resource.make(rootCtx.set(f(parent)))(_ => rootCtx.set(parent)).use(_ => fa)
    }
}
