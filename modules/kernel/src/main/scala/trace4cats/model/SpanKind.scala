package trace4cats.model

import cats.{Order, Show}

sealed abstract class SpanKind(val entryName: String)
object SpanKind {
  case object Server extends SpanKind("SERVER")
  case object Client extends SpanKind("CLIENT")
  case object Producer extends SpanKind("PRODUCER")
  case object Consumer extends SpanKind("CONSUMER")
  case object Internal extends SpanKind("INTERNAL")

  implicit val order: Order[SpanKind] = Order.by(_.entryName)
  implicit val show: Show[SpanKind] = Show.show(_.entryName)
}
