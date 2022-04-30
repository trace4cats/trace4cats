package trace4cats.model

import java.time.Instant

import cats.data.NonEmptyList
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
  status: SpanStatus,
  links: Option[NonEmptyList[Link]],
  metaTrace: Option[MetaTrace]
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
          |  attributes: ${span.allAttributes}
          |  status: ${span.status}
          |  link: ${span.links}
          |  meta-trace: ${span.metaTrace.fold("[ ]")(_.show)}
          |}""".stripMargin
  }

  implicit val instant: Eq[Instant] = Eq.fromUniversalEquals
  implicit val eq: Eq[CompletedSpan] = Eq.by(cs =>
    (cs.context, cs.name, cs.serviceName, cs.kind, cs.start, cs.end, cs.attributes, cs.status, cs.links, cs.metaTrace)
  )

  case class Builder(
    context: SpanContext,
    name: String,
    kind: SpanKind,
    start: Instant,
    end: Instant,
    attributes: Map[String, AttributeValue],
    status: SpanStatus,
    links: Option[NonEmptyList[Link]],
    metaTrace: Option[MetaTrace] = None
  ) {
    def withMetaTrace(trace: MetaTrace): Builder = copy(metaTrace = Some(trace))
    def build(process: TraceProcess): CompletedSpan =
      CompletedSpan(
        context,
        name,
        process.serviceName,
        kind,
        start,
        end,
        process.attributes ++ attributes,
        status,
        links,
        metaTrace
      )
    def build(serviceName: String): CompletedSpan =
      CompletedSpan(context, name, serviceName, kind, start, end, attributes, status, links, metaTrace)
  }
}
