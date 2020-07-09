package io.janstenpickle.trace4cats.opentelemetry

import io.janstenpickle.trace4cats.model.AttributeValue
import io.opentelemetry.common.{AttributeValue => OTAttributeValue}

package object common {
  private[common] def toAttributes[A](
    attributes: Map[String, AttributeValue],
    builder: A,
    add: (A, String, OTAttributeValue) => A
  ): A =
    attributes.foldLeft(builder) {
      case (a, (k, AttributeValue.StringValue(value))) => add(a, k, OTAttributeValue.stringAttributeValue(value))
      case (a, (k, AttributeValue.BooleanValue(value))) => add(a, k, OTAttributeValue.booleanAttributeValue(value))
      case (a, (k, AttributeValue.DoubleValue(value))) => add(a, k, OTAttributeValue.doubleAttributeValue(value))
      case (a, (k, AttributeValue.LongValue(value))) => add(a, k, OTAttributeValue.longAttributeValue(value))
    }
}
