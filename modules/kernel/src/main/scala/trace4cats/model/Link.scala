package trace4cats.model

import cats.{Eq, Show}
import cats.syntax.show._

case class Link(traceId: TraceId, spanId: SpanId)

object Link {
  implicit val show: Show[Link] = Show.show(link => show"Link(traceId = ${link.traceId}, spanId = ${link.spanId})")
  implicit val eq: Eq[Link] = Eq.by(l => (l.traceId, l.spanId))
}
