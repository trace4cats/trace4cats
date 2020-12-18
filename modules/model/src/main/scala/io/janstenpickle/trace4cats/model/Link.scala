package io.janstenpickle.trace4cats.model

import cats.{Eq, Show}

case class Link(traceId: TraceId, spanId: SpanId)

object Link {
  implicit val show: Show[Link] = cats.derived.semiauto.show[Link]
  implicit val eq: Eq[Link] = cats.derived.semiauto.eq[Link]
}
