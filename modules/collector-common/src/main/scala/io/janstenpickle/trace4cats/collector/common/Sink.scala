package io.janstenpickle.trace4cats.collector.common

import cats.Parallel
import cats.effect.{Concurrent, Resource, Timer}
import fs2.Pipe
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.exporter.BufferingExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch

case class Sink[F[_]: Concurrent: Timer: Parallel: Logger] private (bufferSize: Int, exporter: SpanExporter[F]) {
  val pipe: Pipe[F, Batch, Unit] = _.evalMap(exporter.exportBatch)
}

object Sink {
  def apply[F[_]: Concurrent: Timer: Parallel: Logger](
    bufferSize: Int,
    exporters: List[(String, SpanExporter[F])]
  ): Resource[F, Sink[F]] =
    BufferingExporter[F](bufferSize, exporters).map(new Sink[F](bufferSize, _))
}
