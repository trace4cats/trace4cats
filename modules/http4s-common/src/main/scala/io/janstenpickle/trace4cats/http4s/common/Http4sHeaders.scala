package io.janstenpickle.trace4cats.http4s.common

import com.comcast.ip4s.{Ipv4Address, Ipv6Address}
import io.janstenpickle.trace4cats.model.SemanticAttributeKeys._
import io.janstenpickle.trace4cats.model.{AttributeValue, SemanticAttributeKeys, TraceHeaders}
import org.http4s.Headers
import org.typelevel.ci.CIString

object Http4sHeaders {
  def headerFields(
    headers: Headers,
    `type`: String,
    dropWhen: CIString => Boolean = Headers.SensitiveHeaders.contains
  ): List[(String, AttributeValue)] =
    headers.headers.collect {
      case h if !dropWhen(h.name) => s"${`type`}.header.${h.name}" -> AttributeValue.stringToTraceValue(h.value)
    }

  def requestFields(
    req: Request_,
    dropHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains
  ): List[(String, AttributeValue)] =
    List[(String, AttributeValue)](httpMethod -> req.method.name, httpUrl -> req.uri.path.toString) ++ headerFields(
      req.headers,
      "req",
      dropHeadersWhen
    ) ++ req.server.toList.flatMap { address =>
      val addressKey = address.host match {
        case _: Ipv4Address => SemanticAttributeKeys.serviceIpv4
        case _: Ipv6Address => SemanticAttributeKeys.serviceIpv6
      }

      List[(String, AttributeValue)](
        addressKey -> address.host.toString,
        SemanticAttributeKeys.servicePort -> address.port.value
      )
    }

  def responseFields(
    resp: Response_,
    dropHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains
  ): List[(String, AttributeValue)] =
    List[(String, AttributeValue)](
      httpStatusCode -> resp.status.code,
      httpStatusMessage -> resp.status.reason
    ) ++ headerFields(resp.headers, "resp", dropHeadersWhen)

  val converter: TraceHeaders.Converter[Headers] = new TraceHeaders.Converter[Headers] {
    def from(t: Headers): TraceHeaders =
      TraceHeaders(t.headers.map(h => h.name.toString -> h.value).toMap)
    def to(h: TraceHeaders): Headers =
      Headers(h.values.toSeq)
  }
}
