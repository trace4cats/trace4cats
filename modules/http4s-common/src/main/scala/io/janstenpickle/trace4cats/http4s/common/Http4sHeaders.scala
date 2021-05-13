package io.janstenpickle.trace4cats.http4s.common

import io.janstenpickle.trace4cats.model.AttributeValue.{LongValue, StringValue}
import io.janstenpickle.trace4cats.model.SemanticAttributeKeys._
import io.janstenpickle.trace4cats.model.{AttributeValue, TraceHeaders}
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Header, Headers, Uri}
import org.typelevel.ci.CIString

object Http4sHeaders {
  def headerFields(
    headers: Headers,
    `type`: String,
    dropWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  ): List[(String, AttributeValue)] =
    headers.toList.collect {
      case h if !dropWhen(h.name) => s"${`type`}.header.${h.name.value}" -> AttributeValue.stringToTraceValue(h.value)
    }

  def requestFields(
    req: Request_,
    dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  ): List[(String, AttributeValue)] =
    List[(String, AttributeValue)](httpMethod -> req.method.name, httpUrl -> req.uri.path) ++ headerFields(
      req.headers,
      "req",
      dropHeadersWhen
    ) ++ req.uri.host.map { host =>
      val key = host match {
        case _: Uri.Ipv4Address => serviceIpv4
        case _: Uri.Ipv6Address => serviceIpv6
        case _: Uri.RegName => serviceHostname
      }
      key -> StringValue(host.value)
    }.toMap ++ req.uri.port.map(port => servicePort -> LongValue(port.toLong))

  def responseFields(
    resp: Response_,
    dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  ): List[(String, AttributeValue)] =
    List[(String, AttributeValue)](
      httpStatusCode -> resp.status.code,
      httpStatusMessage -> resp.status.reason
    ) ++ headerFields(resp.headers, "resp", dropHeadersWhen)

  val converter: TraceHeaders.Converter[Headers] = new TraceHeaders.Converter[Headers] {
    def from(t: Headers): TraceHeaders =
      TraceHeaders(t.toList.map(h => CIString(h.name.value) -> h.value).toMap)
    def to(h: TraceHeaders): Headers =
      Headers(h.values.map { case (k, v) => Header(k.toString, v) }.toList)
  }
}
