package io.janstenpickle.trace4cats.filtering

import fs2.Pipe
import io.janstenpickle.trace4cats.`export`.StreamSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch

object AttributeFilteringExporter {
  def apply[F[_]](filter: AttributeFilter, underlying: StreamSpanExporter[F]): StreamSpanExporter[F] =
    new StreamSpanExporter[F] {
      private val batchFilter = BatchAttributeFilter(filter)

      override def exportBatch(batch: Batch): F[Unit] = underlying.exportBatch(batchFilter(batch))

      override def pipe: Pipe[F, Batch, Unit] = _.map(batchFilter).through(underlying.pipe)
    }

  def apply[F[_]](filter: AttributeFilter, underlying: SpanExporter[F]): SpanExporter[F] =
    new SpanExporter[F] {
      private val batchFilter = BatchAttributeFilter(filter)

      override def exportBatch(batch: Batch): F[Unit] = underlying.exportBatch(batchFilter(batch))
    }
}
