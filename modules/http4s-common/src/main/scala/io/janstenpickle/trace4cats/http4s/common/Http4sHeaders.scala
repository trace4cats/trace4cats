package io.janstenpickle.trace4cats.http4s.common

import io.janstenpickle.trace4cats.model.AttributeValue
import org.http4s.{Header, Headers, Request, Response}

object Http4sHeaders {
  def headerMap(headers: Headers, `type`: String): List[(String, AttributeValue)] = headers.toList.map { h =>
    s"${`type`}.header.${h.name.value}" -> AttributeValue.stringToTraceValue(h.value)
  }

  def requestFields[F[_]](req: Request[F]): List[(String, AttributeValue)] =
    List[(String, AttributeValue)]("http.method" -> req.method.name, "http.url" -> req.uri.path) ++ headerMap(
      req.headers,
      "req"
    )

  def responseFields[F[_]](resp: Response[F]): List[(String, AttributeValue)] =
    List[(String, AttributeValue)]("http.status_code" -> resp.status.code, "http.status_message" -> resp.status.reason) ++ headerMap(
      resp.headers,
      "resp"
    )

  def reqHeaders[F[_]](req: Request[F]): Map[String, String] =
    req.headers.toList.map { h =>
      h.name.value -> h.value
    }.toMap

  def traceHeadersToHttp(headers: Map[String, String]): List[Header] =
    headers.toList.map {
      case (k, v) => Header(k, v)
    }
}
