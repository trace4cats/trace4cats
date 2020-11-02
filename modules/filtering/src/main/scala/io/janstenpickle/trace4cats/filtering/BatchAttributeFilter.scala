package io.janstenpickle.trace4cats.filtering

import io.janstenpickle.trace4cats.model.{AttributeValue, Batch}

object BatchAttributeFilter {
  def apply(filter: AttributeFilter): Batch => Batch = { batch =>
    def filterAttributes(attributes: Map[String, AttributeValue]): Map[String, AttributeValue] =
      attributes.filterNot(filter.tupled)

    Batch(
      batch.process.copy(attributes = filterAttributes(batch.process.attributes)),
      batch.spans.map(span => span.copy(attributes = filterAttributes(span.attributes)))
    )
  }
}
