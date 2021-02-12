package io.janstenpickle.trace4cats

import io.janstenpickle.trace4cats.model.{AttributeValue, SpanStatus}

sealed trait HandledError

object HandledError {
  case class Status(spanStatus: SpanStatus) extends HandledError
  case class Attribute(key: String, value: AttributeValue) extends HandledError
  case class Attributes(attributes: (String, AttributeValue)*) extends HandledError
  case class StatusAttribute(spanStatus: SpanStatus, attributeKey: String, attributeValue: AttributeValue)
      extends HandledError
  case class StatusAttributes(spanStatus: SpanStatus, attributes: (String, AttributeValue)*) extends HandledError

  implicit def fromSpanStatus(spanStatus: SpanStatus): HandledError = Status(spanStatus)
}
