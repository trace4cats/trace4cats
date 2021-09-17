package io.janstenpickle.trace4cats.model

trait ExternalTraceContext[TraceType, SpanType, ParentType] {
  def traceId: SpanContext => TraceType
  def spanId: SpanContext => SpanType
  def parentId: SpanContext => ParentType
}
