package io.janstenpickle.trace4cats.strackdriver.model

import io.circe.Encoder
import io.circe.generic.semiauto._
import io.janstenpickle.trace4cats.model.TraceValue

case class Attributes(attributeMap: Map[String, AttributeValue], droppedAttributesCount: Int)

object Attributes {
  def fromCompleted(attributes: Map[String, TraceValue]): Attributes = {
    val attrs = attributes.take(32).map {
      case (k, TraceValue.StringValue(value)) => k -> AttributeValue.StringValue(value)
      case (k, TraceValue.DoubleValue(value)) => k -> AttributeValue.IntValue(value.toLong)
      case (k, TraceValue.BooleanValue(value)) => k -> AttributeValue.BoolValue(value)
      case (k, TraceValue.LongValue(value)) => k -> AttributeValue.IntValue(value)
    }

    val attrSize = attrs.size
    val origSize = attributes.size

    val dropped = if (attrSize < origSize) origSize - attrSize else 0

    Attributes(attrs, dropped)
  }

  implicit val encoder: Encoder[Attributes] = deriveEncoder
}
