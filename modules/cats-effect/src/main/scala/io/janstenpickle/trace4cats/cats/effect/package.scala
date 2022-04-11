package io.janstenpickle.trace4cats.cats

import cats.effect.{IO, IOLocal, MonadCancelThrow, Resource}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Local
import io.janstenpickle.trace4cats.inject.Trace

package object effect {

  /** Construct a `Trace` backed by the given `IOLocal[Span[IO]]` */
  def ioLocalTrace(rootSpan: IOLocal[Span[IO]]): Trace[IO] = {
    implicit val local: Local[IO, Span[IO]] = new Local[IO, Span[IO]] {
      val F = MonadCancelThrow[IO]
      def ask[R1 >: Span[IO]]: IO[R1] = rootSpan.get
      def local[A](fa: IO[A])(f: Span[IO] => Span[IO]): IO[A] =
        rootSpan.get.flatMap { parent =>
          Resource.make(rootSpan.set(f(parent)))(_ => rootSpan.set(parent)).use(_ => fa)
        }
    }

    Trace.localSpanInstance[IO, IO]
  }

  /** Construct a `Trace` backed by an `IOLocal[Span[IO]]`, using the given span as the initial value */
  def ioLocalTrace(rootSpan: Span[IO]): IO[Trace[IO]] =
    IOLocal(rootSpan).map(ioLocalTrace)
}
