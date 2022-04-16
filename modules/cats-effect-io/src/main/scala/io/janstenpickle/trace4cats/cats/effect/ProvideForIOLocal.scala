package io.janstenpickle.trace4cats.cats.effect

import cats.arrow.FunctionK
import cats.{~>, Monad}
import cats.effect.kernel.Resource
import cats.effect.{IO, IOLocal}
import io.janstenpickle.trace4cats.base.context.Provide

private[trace4cats] class ProvideForIOLocal[Ctx](rootCtx: IOLocal[Ctx]) extends Provide[IO, IO, Ctx] {
  val Low: Monad[IO] = implicitly
  val F: Monad[IO] = implicitly

  def lift[A](la: IO[A]): IO[A] = la
  def ask[R1 >: Ctx]: IO[R1] = rootCtx.get
  def local[A](fa: IO[A])(f: Ctx => Ctx): IO[A] = rootCtx.get.flatMap { parent =>
    Resource.make(rootCtx.set(f(parent)))(_ => rootCtx.set(parent)).surround(fa)
  }
  def provide[A](fa: IO[A])(r: Ctx): IO[A] = scope(fa)(r)

  override def askUnlift: IO[IO ~> IO] = IO.pure(FunctionK.id)
}
