package io.janstenpickle.trace4cats.opentelemetry.otlp

import java.util.concurrent.TimeUnit

import cats.Foldable
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
import io.opentelemetry.proto.trace.v1.trace.Span.Event
import io.opentelemetry.proto.trace.v1.trace.Status.{DeprecatedStatusCode, StatusCode}
import io.opentelemetry.proto.trace.v1.trace.Status.StatusCode._
import io.opentelemetry.proto.trace.v1.trace.{InstrumentationLibrarySpans, ResourceSpans, Span, Status}
import org.apache.commons.codec.binary.Hex
import scalapb.UnknownFieldSet
import cats.syntax.foldable._
import cats.syntax.semigroup._

import scala.collection.mutable.ListBuffer
object Convert {
  def toAttributes(attributes: Map[String, AttributeValue]): List[KeyValue] =
    attributes.toList.map {
      case (k, AttributeValue.StringValue(v)) =>
        KeyValue(key = k, value = Some(AnyValue.of(AnyValue.Value.StringValue(v.value))))
      case (k, AttributeValue.BooleanValue(v)) =>
        KeyValue(key = k, value = Some(AnyValue.of(AnyValue.Value.BoolValue(v.value))))
      case (k, AttributeValue.DoubleValue(v)) =>
        KeyValue(key = k, value = Some(AnyValue.of(AnyValue.Value.DoubleValue(v.value))))
      case (k, AttributeValue.LongValue(v)) =>
        KeyValue(key = k, value = Some(AnyValue.of(AnyValue.Value.IntValue(v.value))))
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
          code = span.status match {
            case s if s.isOk => STATUS_CODE_OK
            case _ => STATUS_CODE_ERROR
          },
          message = span.status match {
            case Internal(message) => message
            case _ => ""
          }
        )
      ),
      links = span.links.fold(List.empty[Span.Link])(_.map { link =>
        Span.Link(ByteString.copyFrom(link.traceId.value), ByteString.copyFrom(link.spanId.value))
      }.toList)
    )

  def toInstrumentationLibrarySpans(spans: List[CompletedSpan]): InstrumentationLibrarySpans =
    InstrumentationLibrarySpans(
      instrumentationLibrary = Some(InstrumentationLibrary("trace4cats")),
      spans = spans
        .foldLeft(ListBuffer.empty[Span]) { (buf, span) =>
          buf += toSpan(span)
        }
        .toList
    )

  def toResourceSpans[G[_]: Foldable](batch: Batch[G]): Iterable[ResourceSpans] =
    batch.spans
      .foldLeft(Map.empty[String, List[CompletedSpan]]) { (acc, span) =>
        acc |+| Map(span.serviceName -> List(span))
      }
      .map { case (service, spans) =>
        ResourceSpans(
          resource = Some(
            Resource(attributes =
              List(KeyValue("service.name", Some(AnyValue.of(AnyValue.Value.StringValue(service)))))
            )
          ),
          instrumentationLibrarySpans = List(toInstrumentationLibrarySpans(spans))
        )
      }

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
      val updatedKeys = obj.toMap.map { case (k, v) =>
        val chars = k.toCharArray

        chars(0) = Character.toLowerCase(chars(0))

        new String(chars) -> v.hcursor.downField("value").focus.get
      }

      JsonObject.fromMap(updatedKeys)
    }
  implicit def anyValueEncoder: Encoder[AnyValue] = valueEncoder.contramap(_.value)
  implicit def keyValueEncoder: Encoder[KeyValue] = deriveConfiguredEncoder

  implicit val eventEncoder: Encoder[Event] = deriveConfiguredEncoder
  implicit val linkEncoder: Encoder[Span.Link] = deriveConfiguredEncoder
  implicit val statusCodeEncoder: Encoder[StatusCode] = Encoder.encodeInt.contramap(_.value)
  implicit val deprecatedStatusCodeEncoder: Encoder[DeprecatedStatusCode] = Encoder.encodeInt.contramap(_.value)
  implicit val statusEncoder: Encoder[Status] = deriveConfiguredEncoder

  implicit val spanEncoder: Encoder[Span] = deriveConfiguredEncoder

  implicit val instrumentationLibraryEncoder: Encoder[InstrumentationLibrary] = deriveConfiguredEncoder
  implicit val instrumentationLibrarySpansEncoder: Encoder[InstrumentationLibrarySpans] = deriveConfiguredEncoder
  implicit val resourceEncoder: Encoder[Resource] = deriveConfiguredEncoder
  implicit val resourceSpansEncoder: Encoder[ResourceSpans] = deriveConfiguredEncoder
  implicit val resourceSpansIterableEncoder: Encoder[Iterable[ResourceSpans]] =
    Encoder.encodeJsonObject.contramapObject { resourceSpans =>
      JsonObject.fromMap(
        Map("resource_spans" -> Json.fromValues(resourceSpans.map(resourceSpansEncoder(_).deepDropNullValues)))
      )
    }

  def toJsonString[G[_]: Foldable](batch: Batch[G]): String = resourceSpansIterableEncoder(
    toResourceSpans(batch)
  ).noSpaces
}
