package io.janstenpickle.trace4cats.opentelemetry

import io.janstenpickle.trace4cats.model.SpanStatus

package object jaeger {
  val statusCode: SpanStatus => Int = {
    case SpanStatus.Ok => 0
    case _ => 2
  }
}
