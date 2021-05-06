package io.janstenpickle.trace4cats.sttp.client

import io.janstenpickle.trace4cats.model.TraceHeaders
import org.typelevel.ci.CIString
import sttp.model.{Header, Headers}

object SttpHeaders {
  val converter: TraceHeaders.Converter[Headers] = new TraceHeaders.Converter[Headers] {
    def from(t: Headers): TraceHeaders =
      TraceHeaders(t.headers.map(h => CIString(h.name) -> h.value).toMap)
    def to(h: TraceHeaders): Headers =
      Headers(h.values.map { case (k, v) => Header(k.toString, v) }.toList)
  }
}
