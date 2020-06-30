package io.janstenpickle.trace4cats.model

import cats.Show
import enumeratum.EnumEntry.Camelcase
import enumeratum._

sealed trait SpanStatus extends EnumEntry
object SpanStatus extends Enum[SpanStatus] {
  override def values = findValues

  case object Ok extends SpanStatus with Camelcase
  case object Cancelled extends SpanStatus with Camelcase
  case object Internal extends SpanStatus with Camelcase

  implicit val show: Show[SpanStatus] = Show.show(_.entryName)
}
