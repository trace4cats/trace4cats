package io.janstenpickle.trace4cats.stackdriver.model

import io.circe.Encoder
import io.circe.generic.semiauto._

case class SpanLinks(link: List[SpanLink], droppedLinksCount: Int)

object SpanLinks {
  implicit val encoder: Encoder[SpanLinks] = deriveEncoder
}
