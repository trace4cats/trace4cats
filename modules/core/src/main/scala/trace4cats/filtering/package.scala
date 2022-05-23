package trace4cats

package object filtering {
  type AttributeFilter = (String, AttributeValue) => Boolean

  def filterSpanAttributes(filter: AttributeFilter): CompletedSpan => CompletedSpan = {
    def filterAttributes(attributes: Map[String, AttributeValue]): Map[String, AttributeValue] =
      attributes.filterNot(filter.tupled)

    span => span.copy(attributes = filterAttributes(span.attributes))
  }
}
