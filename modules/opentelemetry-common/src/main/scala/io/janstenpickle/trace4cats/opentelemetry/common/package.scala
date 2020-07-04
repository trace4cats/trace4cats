package io.janstenpickle.trace4cats.opentelemetry

import io.janstenpickle.trace4cats.model.TraceValue
import io.opentelemetry.common.AttributeValue

package object common {
  private[common] def toAttributes[A](
    attributes: Map[String, TraceValue],
    builder: A,
    add: (A, String, AttributeValue) => A
  ): A =
    attributes.foldLeft(builder) {
      case (a, (k, TraceValue.StringValue(value))) => add(a, k, AttributeValue.stringAttributeValue(value))
      case (a, (k, TraceValue.BooleanValue(value))) => add(a, k, AttributeValue.booleanAttributeValue(value))
      case (a, (k, TraceValue.DoubleValue(value))) => add(a, k, AttributeValue.doubleAttributeValue(value))
    }
}
