package io.janstenpickle.trace4cats.strackdriver.model

import io.circe.Encoder
import io.circe.generic.semiauto._

case class Batch(spans: List[Span])

object Batch {
  implicit val encoder: Encoder[Batch] = deriveEncoder
}
