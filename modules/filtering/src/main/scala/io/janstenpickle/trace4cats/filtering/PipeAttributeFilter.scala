package io.janstenpickle.trace4cats.filtering

import fs2.Pipe
import io.janstenpickle.trace4cats.model.{AttributeValue, CompletedSpan}

object PipeAttributeFilter {
  def apply[F[_]](filter: AttributeFilter): Pipe[F, CompletedSpan, CompletedSpan] = {
    def filterAttributes(attributes: Map[String, AttributeValue]): Map[String, AttributeValue] =
      attributes.filterNot(filter.tupled)

    _.mapChunks(_.map(span => span.copy(attributes = filterAttributes(span.attributes))))
  }
}
