package io.janstenpickle.trace4cats.model

import enumeratum._

sealed trait Link extends EnumEntry {
  def traceId: TraceId
  def spanId: SpanId
}
object Link extends Enum[Link] with CatsEnum[Link] {
  override def values = findValues

  case class Child(traceId: TraceId, spanId: SpanId) extends Link
  case class Parent(traceId: TraceId, spanId: SpanId) extends Link
}
