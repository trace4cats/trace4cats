package io.janstenpickle.trace4cats.opentelemetry

import io.janstenpickle.trace4cats.model.{AttributeValue, SpanStatus}

package object otlp {
  val statusCode: SpanStatus => Int = {
    case s if s.isOk => 1
    case _ => 2
  }

  val additionalTags: Map[String, AttributeValue] = Map("otlp.instrumentation.library.name" -> "trace4cats")
}
