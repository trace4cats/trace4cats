package io.janstenpickle.trace4cats.model

import cats.{Eq, Show}
import cats.implicits._

case class CompletedSpan(
  context: SpanContext,
  name: String,
  kind: SpanKind,
  start: Long,
  end: Long,
  attributes: Map[String, TraceValue],
  status: SpanStatus
)

object CompletedSpan {
  implicit val show: Show[CompletedSpan] = Show.show { span =>
    show"""{
          |  context: ${span.context}
          |  name: ${span.name}
          |  kind: ${span.kind}
          |  start: ${span.start}
          |  end: ${span.end}
          |  attributes: ${span.attributes}
          |  status: ${span.status}
          |}""".stripMargin
  }

  implicit val eq: Eq[CompletedSpan] = cats.derived.semi.eq[CompletedSpan]
}
