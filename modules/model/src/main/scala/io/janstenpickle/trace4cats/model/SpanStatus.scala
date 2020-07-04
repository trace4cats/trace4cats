package io.janstenpickle.trace4cats.model

import enumeratum.EnumEntry.Camelcase
import enumeratum._

sealed trait SpanStatus extends EnumEntry {
  def canonicalCode: Int
  def isOk: Boolean
}
object SpanStatus extends Enum[SpanStatus] with CatsEnum[SpanStatus] {
  override def values = findValues

  case object Ok extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 0
    override lazy val isOk: Boolean = true
  }
  case object Cancelled extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 1
    override lazy val isOk: Boolean = false
  }
  case object Internal extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 13
    override lazy val isOk: Boolean = false
  }
}
