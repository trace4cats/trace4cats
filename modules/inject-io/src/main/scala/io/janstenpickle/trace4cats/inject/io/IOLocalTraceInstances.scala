package io.janstenpickle.trace4cats.inject.io

import cats.effect.{IO, IOLocal}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Local
import io.janstenpickle.trace4cats.base.optics.Lens
import io.janstenpickle.trace4cats.inject.Trace

trait IOLocalTraceInstances {

  /** Construct a `Trace[IO]` instance backed by the given `IOLocal[Ctx]`. */
  def ioLocalTrace[Ctx](rootCtx: IOLocal[Ctx])(implicit lens: Lens[Ctx, Span[IO]]): Trace[IO] = {
    implicit val local: Local[IO, Span[IO]] = ioLocalProvide(rootCtx).focus(lens)
    Trace[IO]
  }
}
