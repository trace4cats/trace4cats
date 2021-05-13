package io.janstenpickle.trace4cats.zipkin

import cats.Foldable
import cats.syntax.foldable._
import cats.syntax.show._
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import io.janstenpickle.trace4cats.`export`.SemanticTags
import io.janstenpickle.trace4cats.model.AttributeValue.{LongValue, StringValue}
import io.janstenpickle.trace4cats.model._
import io.janstenpickle.trace4cats.zipkin.ZipkinSpan.Endpoint

import java.util.concurrent.TimeUnit

// implements https://zipkin.io/zipkin-api/zipkin2-api.yaml
// does not support 'annotations' and 'debug' fields
case class ZipkinSpan(
  traceId: String,
  name: String,
  parentId: Option[String],
  id: String,
  kind: Option[String],
  timestamp: Long,
  duration: Long,
  shared: Option[Boolean],
  localEndpoint: Option[Endpoint],
  remoteEndpoint: Option[Endpoint],
  tags: Map[String, String]
)

object ZipkinSpan {

  case class Endpoint(serviceName: Option[String], ipv4: Option[String], ipv6: Option[String], port: Option[Int]) {
    def nonEmpty: Boolean = serviceName.isDefined || ipv4.isDefined || ipv6.isDefined || port.isDefined
  }

  private def toTags(span: CompletedSpan): Map[String, String] = {
    val statusTags = SemanticTags.statusTags(prefix = "", requireMessage = false)(span.status)
    (span.attributes ++ statusTags).map { case (k, v) => k -> v.show }
  }

  private def toLocalEndpoint(serviceName: String, attributes: Map[String, AttributeValue]): Option[Endpoint] = {
    val ipv4 = attributes.get(SemanticAttributeKeys.serviceIpv4).collect { case StringValue(address) =>
      address.value
    }
    val ipv6 = attributes.get(SemanticAttributeKeys.serviceIpv6).collect { case StringValue(address) =>
      address.value
    }
    val port = attributes.get(SemanticAttributeKeys.servicePort).collect { case LongValue(port) =>
      port.value.toInt
    }
    Some(Endpoint(Some(serviceName), ipv4, ipv6, port))
  }

  private def toRemoteEndpoint(kind: SpanKind, attributes: Map[String, AttributeValue]): Option[Endpoint] = kind match {
    case SpanKind.Client | SpanKind.Producer =>
      val ipv4 = attributes.get(SemanticAttributeKeys.remoteServiceIpv4).collect { case StringValue(address) =>
        address.value
      }
      val ipv6 = attributes.get(SemanticAttributeKeys.remoteServiceIpv6).collect { case StringValue(address) =>
        address.value
      }
      val port = attributes.get(SemanticAttributeKeys.remoteServicePort).collect { case LongValue(port) =>
        port.value.toInt
      }
      val endpoint = Endpoint(None, ipv4, ipv6, port)
      if (endpoint.nonEmpty) Some(endpoint) else None
    case _ => None
  }

  private def convert(span: CompletedSpan): ZipkinSpan = {
    val startMicros = TimeUnit.MILLISECONDS.toMicros(span.start.toEpochMilli)
    val endMicros = TimeUnit.MILLISECONDS.toMicros(span.end.toEpochMilli)
    ZipkinSpan(
      traceId = span.context.traceId.show,
      name = span.name,
      parentId = span.context.parent.map(_.spanId.show),
      id = span.context.spanId.show,
      kind = span.kind match {
        case SpanKind.Server | SpanKind.Client | SpanKind.Producer | SpanKind.Consumer =>
          Some(span.kind.entryName.toUpperCase)
        case SpanKind.Internal => None
      },
      timestamp = startMicros,
      duration = endMicros - startMicros,
      shared = if (span.context.isRemote) Some(true) else None,
      localEndpoint = toLocalEndpoint(span.serviceName, span.attributes),
      remoteEndpoint = toRemoteEndpoint(span.kind, span.attributes),
      tags = toTags(span)
    )
  }

  implicit val endpointEncoder: Encoder[Endpoint] = deriveEncoder[Endpoint]

  implicit val zipkinSpanEncoder: Encoder[ZipkinSpan] = deriveEncoder[ZipkinSpan]

  def toJsonString[F[_]: Foldable](batch: Batch[F]): String =
    batch.spans.toList.map(convert).asJson.deepDropNullValues.noSpaces

}
