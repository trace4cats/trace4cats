package io.janstenpickle.trace4cats.log

import cats.effect.Sync
import cats.syntax.functor._
import cats.syntax.show._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch

object LogSpanExporter {
  def apply[F[_]: Logger]: SpanExporter[F] = new SpanExporter[F] {
    override def exportBatch(batch: Batch): F[Unit] = Logger[F].info(batch.show)
  }

  def create[F[_]: Sync]: F[SpanExporter[F]] = Slf4jLogger.create[F].map { implicit logger =>
    apply[F]
  }
}
