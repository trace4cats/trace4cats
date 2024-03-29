package trace4cats.context.iolocal

import cats.arrow.FunctionK
import cats.effect.kernel.Resource
import cats.effect.{IO, IOLocal}
import cats.{~>, Monad}
import trace4cats.context.Provide

private[iolocal] class IOLocalProvide[Ctx](rootCtx: IOLocal[Ctx]) extends Provide[IO, IO, Ctx] {
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
