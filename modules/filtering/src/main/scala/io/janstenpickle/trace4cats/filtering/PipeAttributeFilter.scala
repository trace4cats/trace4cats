package io.janstenpickle.trace4cats.filtering

import fs2.Pipe
import io.janstenpickle.trace4cats.model.CompletedSpan

object PipeAttributeFilter {
  def apply[F[_]](filter: AttributeFilter): Pipe[F, CompletedSpan, CompletedSpan] = {
    val spanFilter = filterSpanAttributes(filter)

    _.mapChunks(_.map(spanFilter))
  }
}
