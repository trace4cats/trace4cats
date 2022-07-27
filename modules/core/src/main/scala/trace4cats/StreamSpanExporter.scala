package trace4cats

import cats.effect.kernel.Concurrent
import cats.syntax.functor._
import cats.{Applicative, Monoid, Parallel}
import fs2.{Chunk, Pipe}
import trace4cats.model.Batch
import cats.syntax.parallel._

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
          override def exportBatch(batch: Batch[Chunk]): F[Unit] =
            Parallel.parMap2(x.exportBatch(batch), y.exportBatch(batch))((_, _) => ())
        }

      override def combineAllOption(as: IterableOnce[StreamSpanExporter[F]]): Option[StreamSpanExporter[F]] =
        if (as.iterator.isEmpty) None
        else Some(combineAll(as))

      override def combineAll(as: IterableOnce[StreamSpanExporter[F]]): StreamSpanExporter[F] =
        new StreamSpanExporter[F] {
          override def exportBatch(batch: Batch[Chunk]): F[Unit] = as.iterator.toList.parTraverse_(_.exportBatch(batch))
        }
    }
}
