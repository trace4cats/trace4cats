package io.janstenpickle.trace4cats.model

import scala.math.ScalaNumber

trait ExternalTraceContext {
  def traceId: SpanContext => ScalaNumber
  def spanId: SpanContext => ScalaNumber
}
