package io.janstenpickle.trace4cats.`export`

import cats.effect.Concurrent
import cats.{Applicative, Monoid, Parallel}
import fs2.Pipe
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import cats.syntax.functor._

trait StreamSpanExporter[F[_]] extends SpanExporter[F] {
  def pipe: Pipe[F, Batch, Unit]
}

object StreamSpanExporter {
  def apply[F[_]](exporter: StreamSpanExporter[F]): StreamSpanExporter[F] = exporter

  def empty[F[_]: Applicative]: StreamSpanExporter[F] =
    new StreamSpanExporter[F] {
      override def pipe: Pipe[F, Batch, Unit] = _.void

      override def exportBatch(batch: Batch): F[Unit] = Applicative[F].unit
    }

  implicit def monoid[F[_]: Concurrent: Parallel]: Monoid[StreamSpanExporter[F]] =
    new Monoid[StreamSpanExporter[F]] {
      override def empty: StreamSpanExporter[F] = StreamSpanExporter.empty[F]

      override def combine(x: StreamSpanExporter[F], y: StreamSpanExporter[F]): StreamSpanExporter[F] =
        new StreamSpanExporter[F] {
          override def pipe: Pipe[F, Batch, Unit] = in => in.through(x.pipe).concurrently(in.through(y.pipe))

          override def exportBatch(batch: Batch): F[Unit] =
            Parallel.parMap2(x.exportBatch(batch), y.exportBatch(batch))((_, _) => ())
        }
    }
}
