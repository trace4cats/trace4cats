package trace4cats.io

import cats.effect.{IO, IOLocal}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.inject.Trace
import trace4cats.context.Local
import trace4cats.optics.Lens

trait IOLocalTraceInstances {

  /** Construct a `Trace[IO]` instance backed by the given `IOLocal[Ctx]`. */
  def ioLocalTrace[Ctx](rootCtx: IOLocal[Ctx])(implicit lens: Lens[Ctx, Span[IO]]): Trace.WithContext[IO] = {
    implicit val local: Local[IO, Span[IO]] = ioLocalProvide(rootCtx).focus(lens)
    Trace.WithContext[IO]
  }
}
