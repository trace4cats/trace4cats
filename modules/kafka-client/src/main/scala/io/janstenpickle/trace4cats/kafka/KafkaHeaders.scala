package io.janstenpickle.trace4cats.kafka

import cats.syntax.foldable._
import fs2.kafka.{Header, Headers}
import io.janstenpickle.trace4cats.model.TraceHeaders

object KafkaHeaders {
  val converter: TraceHeaders.Converter[Headers] = new TraceHeaders.Converter[Headers] {
    def from(t: Headers): TraceHeaders =
      t.toChain.foldMap(h => TraceHeaders.of(h.key() -> h.as[String]))
    def to(h: TraceHeaders): Headers =
      Headers.fromIterable(h.values.map { case (k, v) => Header(k.toString, v) })
  }
}
