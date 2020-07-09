package io.janstenpickle.trace4cats.newrelic

import io.circe.{Encoder, Json, JsonObject}
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceValue}
import io.circe.syntax._
import cats.syntax.show._
import io.janstenpickle.trace4cats.`export`.SemanticTags

// Based on API docs found here:
// https://docs.newrelic.com/docs/understand-dependencies/distributed-tracing/trace-api/report-new-relic-format-traces-trace-api
object Convert {
  implicit val traceValueEncoder: Encoder[TraceValue] = Encoder.instance {
    case TraceValue.StringValue(value) => Json.fromString(value)
    case TraceValue.BooleanValue(value) => Json.fromBoolean(value)
    case TraceValue.LongValue(value) => Json.fromLong(value)
    case TraceValue.DoubleValue(value) => Json.fromDoubleOrString(value)
  }

  def attributesJson(attributes: Map[String, TraceValue]): Json =
    Json.fromJsonObject(JsonObject.fromMap(Map("attributes" -> attributes.asJson)))

  def spanJson(span: CompletedSpan): Json =
    Json.fromJsonObject(
      JsonObject.fromMap(
        Map(
          "trace.id" -> Json.fromString(span.context.traceId.show),
          "id" -> Json.fromString(span.context.spanId.show),
          "attributes" ->
            attributesJson(
              span.attributes ++ SemanticTags.kindTags(span.kind) ++ SemanticTags
                .statusTags("")(span.status) ++ Map[String, TraceValue](
                "duration.ms" -> TraceValue.LongValue(span.end.toEpochMilli - span.start.toEpochMilli),
                "name" -> span.name
              ) ++ span.context.parent.map { parent =>
                "parent.id" -> TraceValue.StringValue(parent.spanId.show)
              }.toMap
            )
        )
      )
    )

  def toJson(batch: Batch): Json =
    List(
      JsonObject.fromMap(
        Map(
          "common" -> attributesJson(batch.process.attributes + ("service.name" -> batch.process.serviceName)),
          "spans" -> Json.fromValues(batch.spans.map(spanJson))
        )
      )
    ).asJson
}
