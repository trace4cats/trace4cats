package io.janstenpickle.trace4cats.model

import cats.{Eq, Show}

case class Link(traceId: TraceId, spanId: SpanId)

object Link {
  implicit val show: Show[Link] = Show.fromToString
  implicit val eq: Eq[Link] = Eq.by(l => (l.traceId, l.spanId))
}
