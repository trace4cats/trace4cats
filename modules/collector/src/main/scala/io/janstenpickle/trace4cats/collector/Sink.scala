package io.janstenpickle.trace4cats.collector

import cats.instances.list._
import cats.syntax.foldable._
import cats.{Applicative, Parallel}
import fs2.Pipe
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch

object Sink {
  def apply[F[_]: Applicative: Parallel](exporters: SpanExporter[F]*): Pipe[F, Batch, Unit] = {
    val combined = exporters.toList.combineAll

    _.evalMap(combined.exportBatch)
  }
}
