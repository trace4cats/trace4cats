package io.janstenpickle.trace4cats.log

import cats.effect.Sync
import cats.syntax.functor._
import cats.syntax.show._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.{CompletedSpan, TraceProcess}

object LogSpanCompleter {
  def apply[F[_]: Logger](process: TraceProcess): SpanCompleter[F] =
    new SpanCompleter[F] {
      override def complete(span: CompletedSpan.Builder): F[Unit] = Logger[F].info(span.build(process).show)
    }

  def create[F[_]: Sync](process: TraceProcess): F[SpanCompleter[F]] =
    Slf4jLogger.create[F].map { implicit logger =>
      apply[F](process)
    }
}
