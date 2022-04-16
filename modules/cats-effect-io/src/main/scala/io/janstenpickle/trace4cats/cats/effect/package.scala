package io.janstenpickle.trace4cats.cats

import cats.effect.{IO, IOLocal}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.{Local, Provide}
import io.janstenpickle.trace4cats.base.optics.Lens
import io.janstenpickle.trace4cats.inject.Trace

package object effect {

  def ioLocalProvide(rootSpan: IOLocal[Span[IO]]): Provide[IO, IO, Span[IO]] =
    new ProvideForIOLocal(rootSpan)

  def ioLocalProvideWithContext[Ctx](rootCtx: IOLocal[Ctx]): Provide[IO, IO, Ctx] =
    new ProvideForIOLocal(rootCtx)

  /** Construct a `Trace` backed by the given `IOLocal[Span[IO]]`. */
  def ioLocalTrace(rootSpan: IOLocal[Span[IO]]): Trace[IO] = {
    implicit val local: Local[IO, Span[IO]] = ioLocalProvide(rootSpan)
    Trace[IO]
  }

  /** Construct a `Trace` backed by an `IOLocal[Span[IO]]`, using the given span as the initial value. */
  def ioLocalTrace(rootSpan: Span[IO]): IO[Trace[IO]] =
    IOLocal(rootSpan).map(ioLocalTrace)

  /** Construct a `Trace` backed by the given `IOLocal[Ctx]`. */
  def ioLocalTraceWithContext[Ctx](rootCtx: IOLocal[Ctx])(implicit lens: Lens[Ctx, Span[IO]]): Trace[IO] = {
    implicit val local: Local[IO, Span[IO]] = ioLocalProvideWithContext(rootCtx).focus(lens)
    Trace[IO]
  }

  /** Construct a `Trace` backed by an `IOLocal[Ctx]`, using the given context as the initial value. */
  def ioLocalTraceWithContext[Ctx](rootCtx: Ctx)(implicit lens: Lens[Ctx, Span[IO]]): IO[Trace[IO]] =
    IOLocal(rootCtx).map(ioLocalTraceWithContext(_))

}
