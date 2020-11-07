package io.janstenpickle.trace4cats.filtering

import cats.Functor
import fs2.{Chunk, Pipe}
import io.janstenpickle.trace4cats.`export`.StreamSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan}

object AttributeFilteringExporter {
  def apply[F[_]](filter: AttributeFilter, underlying: StreamSpanExporter[F]): StreamSpanExporter[F] =
    new StreamSpanExporter[F] {
      private val spanFilter = filterSpanAttributes(filter)
      private val batchFilter = BatchAttributeFilter[Chunk](filter)

      override def exportBatch(batch: Batch[Chunk]): F[Unit] = underlying.exportBatch(batchFilter(batch))

      override def pipe: Pipe[F, CompletedSpan, Unit] = _.map(spanFilter).through(underlying.pipe)
    }

  def apply[F[_], G[_]: Functor](filter: AttributeFilter, underlying: SpanExporter[F, G]): SpanExporter[F, G] =
    new SpanExporter[F, G] {
      private val batchFilter = BatchAttributeFilter(filter)

      override def exportBatch(batch: Batch[G]): F[Unit] = underlying.exportBatch(batchFilter(batch))
    }
}
