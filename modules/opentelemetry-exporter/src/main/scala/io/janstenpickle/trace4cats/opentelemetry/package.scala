package io.janstenpickle.trace4cats

import java.util

import io.janstenpickle.trace4cats.model.TraceValue
import io.opentelemetry.common.AttributeValue
import scala.jdk.CollectionConverters._

package object opentelemetry {
  private[opentelemetry] def toAttributes(attributes: Map[String, TraceValue]): util.Map[String, AttributeValue] =
    attributes.toList
      .map {
        case (k, TraceValue.StringValue(value)) => k -> AttributeValue.stringAttributeValue(value)
        case (k, TraceValue.BooleanValue(value)) => k -> AttributeValue.booleanAttributeValue(value)
        case (k, TraceValue.DoubleValue(value)) => k -> AttributeValue.doubleAttributeValue(value)
      }
      .toMap
      .asJava
}
