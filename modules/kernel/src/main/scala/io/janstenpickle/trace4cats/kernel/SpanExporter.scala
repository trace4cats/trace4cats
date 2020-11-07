package io.janstenpickle.trace4cats.kernel

import cats.kernel.Monoid
import cats.{Applicative, ApplicativeError, Apply, Parallel}
import io.janstenpickle.trace4cats.model.Batch

trait SpanExporter[F[_], G[_]] {
  def exportBatch(batch: Batch[G]): F[Unit]
}

object SpanExporter extends LowPrioritySpanExporterInstances {
  def handleErrors[F[_], G[_], E](
    exporter: SpanExporter[F, G]
  )(pf: PartialFunction[E, F[Unit]])(implicit F: ApplicativeError[F, E]): SpanExporter[F, G] =
    new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] = F.recoverWith(exporter.exportBatch(batch))(pf)
    }

  implicit def spanExporterMonoidFromParallel[F[_]: Applicative: Parallel, G[_]]: Monoid[SpanExporter[F, G]] =
    new Monoid[SpanExporter[F, G]] {
      override def combine(x: SpanExporter[F, G], y: SpanExporter[F, G]): SpanExporter[F, G] =
        new SpanExporter[F, G] {
          override def exportBatch(batch: Batch[G]): F[Unit] =
            Parallel.parMap2(x.exportBatch(batch), y.exportBatch(batch))((_, _) => ())
        }

      override def empty: SpanExporter[F, G] = SpanExporter.empty[F, G]
    }
}

trait LowPrioritySpanExporterInstances {
  implicit def spanExporterMonoidFromApply[F[_]: Applicative, G[_]]: Monoid[SpanExporter[F, G]] =
    new Monoid[SpanExporter[F, G]] {
      override def combine(x: SpanExporter[F, G], y: SpanExporter[F, G]): SpanExporter[F, G] =
        new SpanExporter[F, G] {
          override def exportBatch(batch: Batch[G]): F[Unit] =
            Apply[F].map2(x.exportBatch(batch), y.exportBatch(batch))((_, _) => ())
        }

      override def empty: SpanExporter[F, G] = SpanExporter.empty[F, G]
    }

  def empty[F[_]: Applicative, G[_]]: SpanExporter[F, G] =
    new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] = Applicative[F].unit
    }
}
