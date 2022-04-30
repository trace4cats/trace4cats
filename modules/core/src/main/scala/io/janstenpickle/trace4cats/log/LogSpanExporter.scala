package io.janstenpickle.trace4cats.log

import cats.effect.kernel.Sync
import cats.syntax.functor._
import cats.syntax.show._
import cats.{Foldable, Functor}
import trace4cats.kernel.SpanExporter
import trace4cats.model.Batch
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object LogSpanExporter {
  def apply[F[_]: Logger, G[_]: Functor: Foldable]: SpanExporter[F, G] =
    new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] = Logger[F].info(batch.show)
    }

  def create[F[_]: Sync, G[_]: Functor: Foldable]: F[SpanExporter[F, G]] =
    Slf4jLogger.create[F].map { implicit logger =>
      apply[F, G]
    }
}
