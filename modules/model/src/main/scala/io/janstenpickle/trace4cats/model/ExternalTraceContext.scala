package io.janstenpickle.trace4cats.model

trait ExternalTraceContext {
  def traceId: SpanContext => Any
  def spanId: SpanContext => Any
}
