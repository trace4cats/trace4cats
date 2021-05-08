package io.janstenpickle.trace4cats.http4s.common

import io.janstenpickle.trace4cats.model.{AttributeValue, TraceHeaders}
import org.http4s.{Header, Headers}
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
    List[(String, AttributeValue)](
      "http.method" -> req.method.name,
      "http.url" -> req.uri.path.toString
    ) ++ headerFields(req.headers, "req", dropHeadersWhen)

  def responseFields(
    resp: Response_,
    dropHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains
  ): List[(String, AttributeValue)] =
    List[(String, AttributeValue)](
      "http.status_code" -> resp.status.code,
      "http.status_message" -> resp.status.reason
    ) ++ headerFields(resp.headers, "resp", dropHeadersWhen)

  val converter: TraceHeaders.Converter[Headers] = new TraceHeaders.Converter[Headers] {
    def from(t: Headers): TraceHeaders =
      TraceHeaders(t.headers.map(h => h.name -> h.value).toMap)
    def to(h: TraceHeaders): Headers =
      Headers(h.values.toSeq.map { case (k, v) => Header.ToRaw.rawToRaw(Header.Raw(k, v)) }: _*)
  }
}
