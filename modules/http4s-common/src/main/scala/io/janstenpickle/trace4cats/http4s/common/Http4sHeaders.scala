package io.janstenpickle.trace4cats.http4s.common

import io.janstenpickle.trace4cats.model.AttributeValue
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Header, Headers, Request, Response}

object Http4sHeaders {
  def headerMap(
    headers: Headers,
    `type`: String,
    dropWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  ): List[(String, AttributeValue)] =
    headers.toList.collect {
      case h if !dropWhen(h.name) => s"${`type`}.header.${h.name.value}" -> AttributeValue.stringToTraceValue(h.value)
    }

  def requestFields[F[_]](
    req: Request[F],
    dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  ): List[(String, AttributeValue)] =
    List[(String, AttributeValue)]("http.method" -> req.method.name, "http.url" -> req.uri.path) ++ headerMap(
      req.headers,
      "req",
      dropHeadersWhen
    )

  def responseFields[F[_]](
    resp: Response[F],
    dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
  ): List[(String, AttributeValue)] =
    List[(String, AttributeValue)](
      "http.status_code" -> resp.status.code,
      "http.status_message" -> resp.status.reason
    ) ++ headerMap(resp.headers, "resp", dropHeadersWhen)

  def reqHeaders[F[_]](req: Request[F]): Map[String, String] =
    req.headers.toList.map { h =>
      h.name.value -> h.value
    }.toMap

  def traceHeadersToHttp(headers: Map[String, String]): List[Header] =
    headers.toList.map {
      case (k, v) => Header(k, v)
    }
}
