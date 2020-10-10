package io.janstenpickle.trace4cats.opentelemetry

import io.janstenpickle.trace4cats.model.SpanStatus

package object otlp {
  val statusCode: SpanStatus => Int = {
    case SpanStatus.Ok => 0
    case _ => 2
  }
}
