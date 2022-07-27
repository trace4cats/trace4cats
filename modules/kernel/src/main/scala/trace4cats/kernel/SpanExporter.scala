package trace4cats.kernel

import cats.kernel.Monoid
import cats.{Applicative, ApplicativeError, Apply, Parallel}
import trace4cats.model.Batch
import cats.syntax.parallel._
import cats.syntax.foldable._

import scala.collection.compat._

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

      override def combineAllOption(as: IterableOnce[SpanExporter[F, G]]): Option[SpanExporter[F, G]] =
        if (as.iterator.isEmpty) None
        else Some(combineAll(as))

      override def combineAll(as: IterableOnce[SpanExporter[F, G]]): SpanExporter[F, G] =
        new SpanExporter[F, G] {
          override def exportBatch(batch: Batch[G]): F[Unit] = as.iterator.toList.parTraverse_(_.exportBatch(batch))
        }
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

      override def combineAllOption(as: IterableOnce[SpanExporter[F, G]]): Option[SpanExporter[F, G]] =
        if (as.iterator.isEmpty) None
        else Some(combineAll(as))

      override def combineAll(as: IterableOnce[SpanExporter[F, G]]): SpanExporter[F, G] = new SpanExporter[F, G] {
        override def exportBatch(batch: Batch[G]): F[Unit] = as.iterator.toList.traverse_(_.exportBatch(batch))
      }
    }

  def empty[F[_]: Applicative, G[_]]: SpanExporter[F, G] =
    new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] = Applicative[F].unit
    }
}
