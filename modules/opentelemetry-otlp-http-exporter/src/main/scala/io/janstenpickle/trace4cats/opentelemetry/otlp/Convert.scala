package io.janstenpickle.trace4cats.opentelemetry.otlp

import java.util.concurrent.TimeUnit

import cats.syntax.show._
import com.google.protobuf.ByteString
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.{Encoder, Json, JsonObject}
import io.janstenpickle.trace4cats.model.SpanStatus._
import io.janstenpickle.trace4cats.model._
import io.opentelemetry.proto.common.v1.common._
import io.opentelemetry.proto.resource.v1.resource.Resource
import io.opentelemetry.proto.trace.v1.trace.Span.SpanKind._
import io.opentelemetry.proto.trace.v1.trace.Span.{Event, Link}
import io.opentelemetry.proto.trace.v1.trace.Status.StatusCode
import io.opentelemetry.proto.trace.v1.trace.Status.StatusCode._
import io.opentelemetry.proto.trace.v1.trace.{InstrumentationLibrarySpans, ResourceSpans, Span, Status}
import org.apache.commons.codec.binary.Hex
import scalapb.UnknownFieldSet

object Convert {
  def toAttributes(attributes: Map[String, AttributeValue]): List[KeyValue] =
    attributes.toList.map {
      case (k, AttributeValue.StringValue(v)) =>
        KeyValue(key = k, value = Some(AnyValue.of(AnyValue.Value.StringValue(v))))
      case (k, AttributeValue.BooleanValue(v)) =>
        KeyValue(key = k, value = Some(AnyValue.of(AnyValue.Value.BoolValue(v))))
      case (k, AttributeValue.DoubleValue(v)) =>
        KeyValue(key = k, value = Some(AnyValue.of(AnyValue.Value.DoubleValue(v))))
      case (k, AttributeValue.LongValue(v)) =>
        KeyValue(key = k, value = Some(AnyValue.of(AnyValue.Value.IntValue(v))))
      case (k, v: AttributeValue.AttributeList) =>
        KeyValue(key = k, value = Some(AnyValue.of(AnyValue.Value.StringValue(v.show))))
    }

  def toSpan(span: CompletedSpan): Span =
    Span(
      traceId = ByteString.copyFrom(span.context.traceId.value),
      spanId = ByteString.copyFrom(span.context.spanId.value),
      parentSpanId = span.context.parent.fold(ByteString.EMPTY)(parent => ByteString.copyFrom(parent.spanId.value)),
      name = span.name,
      kind = span.kind match {
        case SpanKind.Internal => SPAN_KIND_INTERNAL
        case SpanKind.Client => SPAN_KIND_CLIENT
        case SpanKind.Server => SPAN_KIND_SERVER
        case SpanKind.Producer => SPAN_KIND_PRODUCER
        case SpanKind.Consumer => SPAN_KIND_CONSUMER
      },
      startTimeUnixNano = TimeUnit.MILLISECONDS.toNanos(span.start.toEpochMilli),
      endTimeUnixNano = TimeUnit.MILLISECONDS.toNanos(span.end.toEpochMilli),
      attributes = toAttributes(span.allAttributes),
      status = Some(
        Status(
          span.status match {
            case Ok => STATUS_CODE_OK
            case Cancelled => STATUS_CODE_CANCELLED
            case Unknown => STATUS_CODE_UNKNOWN_ERROR
            case InvalidArgument => STATUS_CODE_INVALID_ARGUMENT
            case DeadlineExceeded => STATUS_CODE_DEADLINE_EXCEEDED
            case NotFound => STATUS_CODE_NOT_FOUND
            case AlreadyExists => STATUS_CODE_ALREADY_EXISTS
            case PermissionDenied => STATUS_CODE_PERMISSION_DENIED
            case ResourceExhausted => STATUS_CODE_RESOURCE_EXHAUSTED
            case FailedPrecondition => STATUS_CODE_FAILED_PRECONDITION
            case Aborted => STATUS_CODE_ABORTED
            case OutOfRange => STATUS_CODE_OUT_OF_RANGE
            case Unimplemented => STATUS_CODE_UNIMPLEMENTED
            case Internal(_) => STATUS_CODE_INTERNAL_ERROR
            case Unavailable => STATUS_CODE_UNAVAILABLE
            case DataLoss => STATUS_CODE_DATA_LOSS
            case Unauthenticated => STATUS_CODE_UNAUTHENTICATED
          },
          span.status match {
            case Internal(message) => message
            case _ => ""
          }
        )
      )
    )

  def toInstrumentationLibrarySpans(spans: List[CompletedSpan]): InstrumentationLibrarySpans =
    InstrumentationLibrarySpans(
      instrumentationLibrary = Some(InstrumentationLibrary("trace4cats")),
      spans = spans.map(toSpan)
    )

  def toResourceSpans(batch: Batch): ResourceSpans =
    ResourceSpans(
      resource = Some(Resource()),
      instrumentationLibrarySpans = List(toInstrumentationLibrarySpans(batch.spans))
    )

  implicit val jsonConfig = Configuration.default.withSnakeCaseConstructorNames.withSnakeCaseMemberNames

  implicit val unknownFieldSetEncoder: Encoder[UnknownFieldSet] = Encoder.instance(_ => Json.Null)

  implicit val byteStringEncoder: Encoder[ByteString] = Encoder.encodeString.contramap { bs =>
    Hex.encodeHexString(bs.toByteArray)
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
