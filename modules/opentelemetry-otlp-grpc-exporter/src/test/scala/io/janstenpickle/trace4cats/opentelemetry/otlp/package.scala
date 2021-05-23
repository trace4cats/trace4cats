package io.janstenpickle.trace4cats.opentelemetry

import io.janstenpickle.trace4cats.model.{AttributeValue, SpanStatus, TraceProcess}

package object otlp {
  val statusCode: SpanStatus => Int = {
    case s if s.isOk => 1
    case _ => 2
  }

  val processTags: TraceProcess => Map[String, AttributeValue] = _ => Map.empty

  val additionalTags: Map[String, AttributeValue] = Map("otel.library.name" -> "trace4cats")

  val excludedTagKeys: Set[String] = Set("ip")
}
