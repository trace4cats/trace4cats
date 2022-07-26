package trace4cats

import cats.syntax.functor._
import cats.syntax.parallel._
import cats.{Applicative, Parallel}
import fs2.{Chunk, Pipe}

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

  def combined[F[_]: Parallel](exporters: List[StreamSpanExporter[F]]): StreamSpanExporter[F] =
    new StreamSpanExporter[F] {
      override def exportBatch(batch: Batch[Chunk]): F[Unit] = exporters.parTraverse_(_.exportBatch(batch))
    }
}
