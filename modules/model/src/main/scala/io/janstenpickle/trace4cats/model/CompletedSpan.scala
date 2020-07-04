package io.janstenpickle.trace4cats.model

import java.time.Instant

import cats.{Eq, Show}
import cats.implicits._

case class CompletedSpan(
  context: SpanContext,
  name: String,
  kind: SpanKind,
  start: Instant,
  end: Instant,
  attributes: Map[String, TraceValue],
  status: SpanStatus
)

object CompletedSpan {
  implicit val instantShow: Show[Instant] = Show.show(_.toString)

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

  implicit val instant: Eq[Instant] = Eq.fromUniversalEquals
  implicit val eq: Eq[CompletedSpan] = cats.derived.semi.eq[CompletedSpan]
}
