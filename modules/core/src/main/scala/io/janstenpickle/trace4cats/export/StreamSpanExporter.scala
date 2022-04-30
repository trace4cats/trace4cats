package io.janstenpickle.trace4cats.`export`

import cats.effect.kernel.Concurrent
import cats.{Applicative, Monoid, Parallel}
import fs2.{Chunk, Pipe}
import trace4cats.kernel.SpanExporter
import trace4cats.model.{Batch, CompletedSpan}
import cats.syntax.functor._

trait StreamSpanExporter[F[_]] extends SpanExporter[F, Chunk] {
  def pipe: Pipe[F, CompletedSpan, Unit] = _.chunks.evalMap(chunk => exportBatch(Batch(chunk)))
}

object StreamSpanExporter {
  def apply[F[_]](exporter: StreamSpanExporter[F]): StreamSpanExporter[F] = exporter

  def empty[F[_]: Applicative]: StreamSpanExporter[F] =
    new StreamSpanExporter[F] {
      override def pipe: Pipe[F, CompletedSpan, Unit] = _.void

      override def exportBatch(batch: Batch[Chunk]): F[Unit] = Applicative[F].unit
    }

  implicit def monoid[F[_]: Concurrent: Parallel]: Monoid[StreamSpanExporter[F]] =
    new Monoid[StreamSpanExporter[F]] {
      override def empty: StreamSpanExporter[F] = StreamSpanExporter.empty[F]

      override def combine(x: StreamSpanExporter[F], y: StreamSpanExporter[F]): StreamSpanExporter[F] =
        new StreamSpanExporter[F] {
          override def pipe: Pipe[F, CompletedSpan, Unit] = in => in.through(x.pipe).concurrently(in.through(y.pipe))

          override def exportBatch(batch: Batch[Chunk]): F[Unit] =
            Parallel.parMap2(x.exportBatch(batch), y.exportBatch(batch))((_, _) => ())
        }
    }
}
