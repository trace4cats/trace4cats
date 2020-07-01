package io.janstenpickle.trace4cats.model

import enumeratum.EnumEntry.Camelcase
import enumeratum._

sealed trait SpanStatus extends EnumEntry
object SpanStatus extends Enum[SpanStatus] with CatsEnum[SpanStatus] {
  override def values = findValues

  case object Ok extends SpanStatus with Camelcase
  case object Cancelled extends SpanStatus with Camelcase
  case object Internal extends SpanStatus with Camelcase
}
