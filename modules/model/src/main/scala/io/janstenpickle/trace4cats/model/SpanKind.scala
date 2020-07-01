package io.janstenpickle.trace4cats.model

import enumeratum.EnumEntry.Uppercase
import enumeratum._

sealed trait SpanKind extends EnumEntry
object SpanKind extends Enum[SpanKind] with CatsEnum[SpanKind] {
  override def values = findValues

  case object Server extends SpanKind with Uppercase
  case object Client extends SpanKind with Uppercase
  case object Producer extends SpanKind with Uppercase
  case object Consumer extends SpanKind with Uppercase
  case object Internal extends SpanKind with Uppercase
}
