package io.janstenpickle.trace4cats.cats.effect

import cats.Monad
import cats.effect.{IO, IOLocal, Resource}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Local
import io.janstenpickle.trace4cats.base.optics.Lens

private[trace4cats] class SpanLocalForIOLocal[Ctx](rootSpan: IOLocal[Ctx])(implicit lens: Lens[Ctx, Span[IO]])
    extends Local[IO, Span[IO]] {
  val F: Monad[IO] = Monad[IO]
  def ask[R1 >: Span[IO]]: IO[R1] = rootSpan.get.map(lens.get)
  def local[A](fa: IO[A])(f: Span[IO] => Span[IO]): IO[A] =
    rootSpan.get.flatMap { parent =>
      Resource.make(rootSpan.set(lens.modify(f)(parent)))(_ => rootSpan.set(parent)).use(_ => fa)
    }
}

private[trace4cats] class ContextLocalForIOLocal[Ctx](rootCtx: IOLocal[Ctx]) extends Local[IO, Ctx] {
  val F: Monad[IO] = Monad[IO]
  def ask[R1 >: Ctx]: IO[R1] = rootCtx.get
  def local[A](fa: IO[A])(f: Ctx => Ctx): IO[A] =
    rootCtx.get.flatMap { parent =>
      Resource.make(rootCtx.set(f(parent)))(_ => rootCtx.set(parent)).use(_ => fa)
    }
}
