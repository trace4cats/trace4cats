package io.janstenpickle.trace4cats.log

import cats.effect.Sync
import cats.syntax.functor._
import cats.syntax.show._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan}

object LogCompleter {
  def apply[F[_]: Logger]: SpanCompleter[F] = new SpanCompleter[F] {
    override def complete(span: CompletedSpan): F[Unit] = Logger[F].info(span.show)
    override def completeBatch(batch: Batch): F[Unit] = Logger[F].info(batch.show)
  }

  def create[F[_]: Sync]: F[SpanCompleter[F]] = Slf4jLogger.create[F].map { implicit logger =>
    apply[F]
  }
}
