package io.janstenpickle.trace4cats.filtering

import fs2.Pipe
import io.janstenpickle.trace4cats.model.Batch

object PipeAttributeFilter {
  def apply[F[_]](filter: AttributeFilter): Pipe[F, Batch, Batch] = {
    val filterBatch = BatchAttributeFilter(filter)

    _.map(filterBatch)
  }
}
