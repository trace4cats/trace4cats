package io.janstenpickle.trace4cats.sttp.common

import io.janstenpickle.trace4cats.model.{AttributeValue, TraceHeaders}
import sttp.model.HeaderNames.isSensitive
import sttp.model.{Header, Headers}

object SttpHeaders {
  def requestFields(hs: Headers, dropHeadersWhen: String => Boolean = isSensitive): List[(String, AttributeValue)] =
    headerFields(hs, "req", dropHeadersWhen)

  def responseFields(hs: Headers, dropHeadersWhen: String => Boolean = isSensitive): List[(String, AttributeValue)] =
    headerFields(hs, "resp", dropHeadersWhen)

  def headerFields(
    hs: Headers,
    `type`: String,
    dropHeadersWhen: String => Boolean = isSensitive
  ): List[(String, AttributeValue)] =
    hs.headers
      .filter(h => !dropHeadersWhen(h.name))
      .map { h =>
        (s"${`type`}.header.${h.name}", h.value: AttributeValue)
      }
      .toList

  val converter: TraceHeaders.Converter[Headers] = new TraceHeaders.Converter[Headers] {
    def from(t: Headers): TraceHeaders =
      TraceHeaders.of(t.headers.map(h => h.name -> h.value).toMap)
    def to(h: TraceHeaders): Headers =
      Headers(h.values.map { case (k, v) => Header(k.toString, v) }.toList)
  }
}
