package io.janstenpickle.trace4cats.model

trait ExternalTraceContext[A] {
  def traceId: SpanContext => A
  def spanId: SpanContext => A
}
