package io.janstenpickle.trace4cats.cats

import cats.effect.{IO, IOLocal}
import cats.~>
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Unlift.IdUnlift
import io.janstenpickle.trace4cats.base.context.{Local, Provide}
import io.janstenpickle.trace4cats.base.optics.Lens
import io.janstenpickle.trace4cats.inject.Trace

package object effect {

  /** Construct a `Trace` backed by the given `IOLocal[Span[IO]]` */
  def ioLocalTrace(rootSpan: IOLocal[Span[IO]]): Trace[IO] = {
    implicit val local: Local[IO, Span[IO]] = new SpanLocalForIOLocal(rootSpan)(Lens.id)
    Trace.localSpanInstance[IO, IO]
  }

  def ioLocalTraceWithContext[Ctx](rootCtx: IOLocal[Ctx])(implicit lens: Lens[Ctx, Span[IO]]): Trace[IO] = {
    implicit val local: Local[IO, Span[IO]] = new SpanLocalForIOLocal(rootCtx)
    Trace.localSpanInstance[IO, IO]
  }

  def ioLocalProvide(rootSpan: IOLocal[Span[IO]]): Provide[IO, IO, Span[IO]] =
    new SpanLocalForIOLocal(rootSpan)(Lens.id) with Provide[IO, IO, Span[IO]] with IdUnlift[IO] {
      override def provide[A](fa: IO[A])(r: Span[IO]): IO[A] = local(fa)(_ => r)
      override def askUnlift: IO[IO ~> IO] = super.askUnlift
    }

  def ioLocalProvideWithContext[Ctx](rootCtx: IOLocal[Ctx]): Provide[IO, IO, Ctx] =
    new ContextLocalForIOLocal(rootCtx) with Provide[IO, IO, Ctx] with IdUnlift[IO] {
      override def provide[A](fa: IO[A])(r: Ctx): IO[A] = local(fa)(_ => r)
      override def askUnlift: IO[IO ~> IO] = super.askUnlift
    }

  /** Construct a `Trace` backed by an `IOLocal[Span[IO]]`, using the given span as the initial value */
  def ioLocalTrace(rootSpan: Span[IO]): IO[Trace[IO]] =
    IOLocal(rootSpan).map(ioLocalTrace)
}
