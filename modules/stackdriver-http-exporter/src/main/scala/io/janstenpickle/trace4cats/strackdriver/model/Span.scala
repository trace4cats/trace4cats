package io.janstenpickle.trace4cats.strackdriver.model

import java.time.Instant
import java.util.concurrent.TimeUnit

import cats.data.NonEmptyList
import cats.syntax.show._
import io.circe.generic.semiauto._
import io.circe.{Encoder, JsonObject}
import io.janstenpickle.trace4cats.model.{CompletedSpan, Link, SpanKind}
import io.janstenpickle.trace4cats.stackdriver.common.StackdriverConstants._
import io.janstenpickle.trace4cats.stackdriver.common.TruncatableString

case class Span(
  name: String,
  spanId: String,
  parentSpanId: Option[String],
  displayName: TruncatableString,
  startTime: Instant,
  endTime: Instant,
  attributes: Attributes,
  stackTrace: JsonObject = JsonObject.empty,
  timeEvents: JsonObject = JsonObject.empty,
  links: SpanLinks,
  status: Status,
  sameProcessAsParentSpan: Option[Boolean],
  childSpanCount: Option[Int] = None,
  spanKind: SpanKind
)

object Span {

  def toDisplayName(spanName: String, spanKind: SpanKind) =
    spanKind match {
      case SpanKind.Server if !spanName.startsWith(ServerPrefix) => ServerPrefix + spanName
      case SpanKind.Client if !spanName.startsWith(ClientPrefix) => ClientPrefix + spanName
      case SpanKind.Consumer if !spanName.startsWith(ServerPrefix) => ServerPrefix + spanName
      case SpanKind.Producer if !spanName.startsWith(ClientPrefix) => ClientPrefix + spanName
      case _ => spanName
    }

  def toSpanLinks(links: Option[NonEmptyList[Link]]): SpanLinks =
    SpanLinks(
      links.fold(List.empty[SpanLink])(_.map {
        case Link.Child(traceId, spanId) => SpanLink(traceId.show, spanId.show, "CHILD_LINKED_SPAN")
        case Link.Parent(traceId, spanId) => SpanLink(traceId.show, spanId.show, "PARENT_LINKED_SPAN")
      }.toList),
      0
    )

  def toInstant(time: Long) = Instant.ofEpochMilli(TimeUnit.MICROSECONDS.toMillis(time))

  def fromCompleted(projectId: String, completed: CompletedSpan): Span =
    Span(
      name = s"projects/$projectId/traces/${completed.context.traceId.show}/spans/${completed.context.spanId.show}",
      spanId = completed.context.spanId.show,
      parentSpanId = completed.context.parent.map(_.spanId.show),
      displayName = TruncatableString(toDisplayName(completed.name, completed.kind)),
      startTime = completed.start,
      endTime = completed.end,
      attributes = Attributes.fromCompleted(completed.allAttributes),
      status = Status(completed.status.canonicalCode),
      sameProcessAsParentSpan = completed.context.parent.map(!_.isRemote),
      spanKind = completed.kind,
      links = toSpanLinks(completed.links)
    )

  implicit val truncatableStringEncoder: Encoder[TruncatableString] = deriveEncoder

  implicit val spanKindEncoder: Encoder[SpanKind] = Encoder.encodeString.contramap(_.entryName.toUpperCase)

  implicit val encoder: Encoder[Span] = deriveEncoder
}
