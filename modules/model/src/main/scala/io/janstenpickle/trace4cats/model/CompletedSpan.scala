package io.janstenpickle.trace4cats.model

import java.time.Instant

import cats.{Eq, Show}
import cats.syntax.all._

case class CompletedSpan(
  context: SpanContext,
  name: String,
  serviceName: String,
  kind: SpanKind,
  start: Instant,
  end: Instant,
  attributes: Map[String, AttributeValue],
  status: SpanStatus
) {
  lazy val allAttributes: Map[String, AttributeValue] =
    attributes.updated("service.name", AttributeValue.StringValue(serviceName))
}

object CompletedSpan {
  implicit val instantShow: Show[Instant] = Show.show(_.toString)

  implicit val show: Show[CompletedSpan] = Show.show { span =>
    show"""{
          |  context: ${span.context}
          |  name: ${span.name}
          |  service: ${span.serviceName}
          |  kind: ${span.kind}
          |  start: ${span.start}
          |  end: ${span.end}
          |  attributes: ${span.attributes}
          |  status: ${span.status}
          |}""".stripMargin
  }

  implicit val instant: Eq[Instant] = Eq.fromUniversalEquals
  implicit val eq: Eq[CompletedSpan] = cats.derived.semiauto.eq[CompletedSpan]

  case class Builder(
    context: SpanContext,
    name: String,
    kind: SpanKind,
    start: Instant,
    end: Instant,
    attributes: Map[String, AttributeValue],
    status: SpanStatus
  ) {
    def build(process: TraceProcess): CompletedSpan =
      CompletedSpan(context, name, process.serviceName, kind, start, end, process.attributes ++ attributes, status)
    def build(serviceName: String): CompletedSpan =
      CompletedSpan(context, name, serviceName, kind, start, end, attributes, status)
  }
}
