package io.janstenpickle.trace4cats.strackdriver.model

import io.circe.Encoder
import io.circe.generic.semiauto._

case class SpanLink(traceId: String, spanId: String, `type`: String, attributes: Attributes = Attributes(Map.empty, 0))

object SpanLink {
  implicit val encoder: Encoder[SpanLink] = deriveEncoder
}
