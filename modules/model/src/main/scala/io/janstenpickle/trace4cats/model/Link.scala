package io.janstenpickle.trace4cats.model

import cats.{Eq, Show}

case class Link(traceId: TraceId, spanId: SpanId)

object Link {
  implicit val show: Show[Link] = magnolify.cats.semiauto.ShowDerivation[Link]
  implicit val eq: Eq[Link] = magnolify.cats.semiauto.EqDerivation[Link]
}
