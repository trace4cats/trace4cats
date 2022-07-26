package trace4cats.kernel

import cats.syntax.parallel._
import cats.{Applicative, ApplicativeError, Parallel}
import trace4cats.model.Batch

trait SpanExporter[F[_], G[_]] {
  def exportBatch(batch: Batch[G]): F[Unit]
}

object SpanExporter {
  def handleErrors[F[_], G[_], E](
    exporter: SpanExporter[F, G]
  )(pf: PartialFunction[E, F[Unit]])(implicit F: ApplicativeError[F, E]): SpanExporter[F, G] =
    new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] = F.recoverWith(exporter.exportBatch(batch))(pf)
    }

  def empty[F[_]: Applicative, G[_]]: SpanExporter[F, G] =
    new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] = Applicative[F].unit
    }

  def combined[F[_]: Parallel, G[_]](exporters: List[SpanExporter[F, G]]): SpanExporter[F, G] = new SpanExporter[F, G] {
    override def exportBatch(batch: Batch[G]): F[Unit] = exporters.parTraverse_(_.exportBatch(batch))
  }
}
