package trace4cats.filtering

import cats.Functor
import cats.syntax.functor._
import trace4cats.model.Batch

object BatchAttributeFilter {
  def apply[G[_]: Functor](filter: AttributeFilter): Batch[G] => Batch[G] = {
    val spanFilter = filterSpanAttributes(filter)

    batch => Batch(batch.spans.map(spanFilter))
  }
}
