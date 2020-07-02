package io.janstenpickle.trace4cats.kernel

import cats.kernel.Monoid
import cats.{Applicative, Apply, Parallel}
import io.janstenpickle.trace4cats.model.Batch

trait SpanExporter[F[_]] {
  def exportBatch(batch: Batch): F[Unit]
}

object SpanExporter extends LowPrioritySpanExporterInstances {
  implicit def spanExporterMonoidFromParallel[F[_]: Applicative: Parallel]: Monoid[SpanExporter[F]] =
    new Monoid[SpanExporter[F]] {
      override def combine(x: SpanExporter[F], y: SpanExporter[F]): SpanExporter[F] =
        new SpanExporter[F] {
          override def exportBatch(batch: Batch): F[Unit] =
            Parallel.parMap2(x.exportBatch(batch), y.exportBatch(batch))((_, _) => ())
        }

      override def empty: SpanExporter[F] = SpanExporter.empty[F]
    }
}

trait LowPrioritySpanExporterInstances {
  implicit def spanExporterMonoidFromApply[F[_]: Applicative]: Monoid[SpanExporter[F]] =
    new Monoid[SpanExporter[F]] {
      override def combine(x: SpanExporter[F], y: SpanExporter[F]): SpanExporter[F] =
        new SpanExporter[F] {
          override def exportBatch(batch: Batch): F[Unit] =
            Apply[F].map2(x.exportBatch(batch), y.exportBatch(batch))((_, _) => ())
        }

      override def empty: SpanExporter[F] = SpanExporter.empty[F]
    }

  def empty[F[_]: Applicative]: SpanExporter[F] = new SpanExporter[F] {
    override def exportBatch(batch: Batch): F[Unit] = Applicative[F].unit
  }
}
