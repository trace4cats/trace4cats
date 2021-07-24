package io.janstenpickle.trace4cats.model

import cats.{Eq, Show}
import cats.syntax.show._

case class MetaTrace(traceId: TraceId, spanId: SpanId)

object MetaTrace {
  implicit val show: Show[MetaTrace] = Show.show(meta => show"[ ${meta.traceId} ${meta.spanId} ]")

  implicit val eq: Eq[MetaTrace] = Eq.by(mt => (mt.traceId, mt.spanId))
}
