package io.janstenpickle.trace4cats.opentelemetry.otlp

import java.util.Base64
import java.util.concurrent.TimeUnit

import com.google.protobuf.ByteString
import io.circe.{Encoder, Json, JsonObject}
import io.circe.generic.extras.Configuration
import io.janstenpickle.trace4cats.model._
import io.opentelemetry.proto.common.v1.common.{AnyValue, ArrayValue, InstrumentationLibrary, KeyValue, KeyValueList}
import io.opentelemetry.proto.resource.v1.resource.Resource
import io.opentelemetry.proto.trace.v1.trace.Span.SpanKind._
import io.opentelemetry.proto.trace.v1.trace.{InstrumentationLibrarySpans, ResourceSpans, Span, Status}
import scalapb.UnknownFieldSet
import io.circe.generic.extras.semiauto._
import io.opentelemetry.proto.trace.v1.trace.Span.{Event, Link}
import io.opentelemetry.proto.trace.v1.trace.Status.StatusCode

object Convert {
  def toAttributes(attributes: Map[String, TraceValue]): List[KeyValue] =
    attributes.toList.map {
      case (k, TraceValue.StringValue(v)) =>
        KeyValue(key = k, value = Some(AnyValue.of(AnyValue.Value.StringValue(v))), UnknownFieldSet.empty)
      case (k, TraceValue.BooleanValue(v)) =>
        KeyValue(key = k, value = Some(AnyValue.of(AnyValue.Value.BoolValue(v))), UnknownFieldSet.empty)
      case (k, TraceValue.DoubleValue(v)) =>
        KeyValue(key = k, value = Some(AnyValue.of(AnyValue.Value.DoubleValue(v))), UnknownFieldSet.empty)
      case (k, TraceValue.LongValue(v)) =>
        KeyValue(key = k, value = Some(AnyValue.of(AnyValue.Value.IntValue(v))), UnknownFieldSet.empty)
    }

  def toResource(process: TraceProcess): Resource =
    Resource(attributes = toAttributes(process.attributes + ("service.name" -> process.serviceName)))

  def toSpan(span: CompletedSpan): Span =
    Span(
      traceId = ByteString.copyFrom(span.context.traceId.value),
      spanId = ByteString.copyFrom(span.context.spanId.value),
      parentSpanId = span.context.parent.fold(ByteString.EMPTY)(parent => ByteString.copyFrom(parent.spanId.value)),
      name = span.name,
      kind = span.kind match {
        case SpanKind.Internal => INTERNAL
        case SpanKind.Client => CLIENT
        case SpanKind.Server => SERVER
        case SpanKind.Producer => PRODUCER
        case SpanKind.Consumer => CONSUMER
      },
      startTimeUnixNano = TimeUnit.MILLISECONDS.toNanos(span.start.toEpochMilli),
      endTimeUnixNano = TimeUnit.MILLISECONDS.toNanos(span.end.toEpochMilli),
      attributes = toAttributes(span.attributes),
      status = Some(Status(span.status match {
        case SpanStatus.Ok => Status.StatusCode.Ok
        case SpanStatus.Cancelled => Status.StatusCode.Cancelled
        case SpanStatus.Internal => Status.StatusCode.InternalError
      }))
    )

  def toInstrumentationLibrarySpans(spans: List[CompletedSpan]): InstrumentationLibrarySpans =
    InstrumentationLibrarySpans(
      instrumentationLibrary = Some(InstrumentationLibrary("trace4cats")),
      spans = spans.map(toSpan)
    )

  def toResourceSpans(batch: Batch): ResourceSpans =
    ResourceSpans(
      resource = Some(toResource(batch.process)),
      instrumentationLibrarySpans = List(toInstrumentationLibrarySpans(batch.spans))
    )

  implicit val jsonConfig = Configuration.default.withSnakeCaseConstructorNames.withSnakeCaseMemberNames

  implicit val unknownFieldSetEncoder: Encoder[UnknownFieldSet] = Encoder.instance(_ => Json.Null)

  implicit val byteStringEncoder: Encoder[ByteString] = Encoder.encodeString.contramap { bs =>
    Base64.getEncoder.encodeToString(bs.toByteArray)
  }
  implicit val spanKindEncoder: Encoder[Span.SpanKind] = Encoder.encodeInt.contramap(_.value)

  implicit val arrayValueEncoder: Encoder[ArrayValue] = Encoder.encodeSeq[AnyValue].contramap(_.values)
  implicit val keyValueListEncoder: Encoder[KeyValueList] = Encoder.encodeSeq[KeyValue].contramap(_.values)

  implicit def valueEncoder: Encoder[AnyValue.Value] =
    io.circe.generic.semiauto.deriveEncoder[AnyValue.Value].mapJsonObject { obj =>
      val updatedKeys = obj.toMap.map {
        case (k, v) =>
          val chars = k.toCharArray

          chars(0) = Character.toLowerCase(chars(0))

          new String(chars) -> v.hcursor.downField("value").focus.get
      }

      JsonObject.fromMap(updatedKeys)
    }
  implicit def anyValueEncoder: Encoder[AnyValue] = valueEncoder.contramap(_.value)
  implicit def keyValueEncoder: Encoder[KeyValue] = deriveConfiguredEncoder

  implicit val eventEncoder: Encoder[Event] = deriveConfiguredEncoder
  implicit val linkEncoder: Encoder[Link] = deriveConfiguredEncoder
  implicit val statusCodeEncoder: Encoder[StatusCode] = Encoder.encodeInt.contramap(_.value)
  implicit val statusEncoder: Encoder[Status] = deriveConfiguredEncoder

  implicit val spanEncoder: Encoder[Span] = deriveConfiguredEncoder

  implicit val instrumentationLibraryEncoder: Encoder[InstrumentationLibrary] = deriveConfiguredEncoder
  implicit val instrumentationLibrarySpansEncoder: Encoder[InstrumentationLibrarySpans] = deriveConfiguredEncoder
  implicit val resourceEncoder: Encoder[Resource] = deriveConfiguredEncoder
  implicit val resourceSpansEncoder: Encoder[ResourceSpans] = deriveConfiguredEncoder[ResourceSpans].mapJsonObject {
    obj =>
      JsonObject.fromMap(
        Map(
          "resource_spans" -> Json
            .fromValues(List(Json.fromJsonObject(obj).deepDropNullValues))
        )
      )
  }

  def toJsonString(batch: Batch): String = resourceSpansEncoder(toResourceSpans(batch)).spaces2
}
