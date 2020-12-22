package io.janstenpickle.trace4cats.sttp.common

import io.janstenpickle.trace4cats.model.{AttributeValue, TraceHeaders}
import sttp.model.HeaderNames.isSensitive
import sttp.model.{Header, Headers}

object SttpHeaders {
  def headerFields(hs: Headers, dropHeadersWhen: String => Boolean = isSensitive): List[(String, AttributeValue)] =
    hs.headers
      .filter(h => !dropHeadersWhen(h.name))
      .map { h =>
        (s"req.header.${h.name}", h.value: AttributeValue)
      }
      .to(List)

  val converter: TraceHeaders.Converter[Headers] = new TraceHeaders.Converter[Headers] {
    def from(t: Headers): TraceHeaders =
      TraceHeaders(t.headers.map(h => h.name -> h.value).toMap)
    def to(h: TraceHeaders): Headers =
      Headers(h.values.map { case (k, v) => Header(k, v) }.toList)
  }
}
