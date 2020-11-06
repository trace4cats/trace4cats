package io.janstenpickle.trace4cats.newrelic

import io.circe.{Encoder, Json, JsonObject}
import io.janstenpickle.trace4cats.model.{AttributeValue, Batch, CompletedSpan}
import io.circe.syntax._
import cats.syntax.show._
import io.janstenpickle.trace4cats.`export`.SemanticTags

// Based on API docs found here:
// https://docs.newrelic.com/docs/understand-dependencies/distributed-tracing/trace-api/report-new-relic-format-traces-trace-api
object Convert {
  implicit val traceValueEncoder: Encoder[AttributeValue] = Encoder.instance {
    case AttributeValue.StringValue(value) => Json.fromString(value)
    case AttributeValue.BooleanValue(value) => Json.fromBoolean(value)
    case AttributeValue.LongValue(value) => Json.fromLong(value)
    case AttributeValue.DoubleValue(value) => Json.fromDoubleOrString(value)
    case value: AttributeValue.AttributeList => Json.fromString(value.show)
  }

  def attributesJson(attributes: Map[String, AttributeValue]): Json =
    Json.fromJsonObject(JsonObject.fromMap(Map("attributes" -> attributes.asJson)))

  def spanJson(span: CompletedSpan): Json =
    Json.fromJsonObject(
      JsonObject.fromMap(
        Map(
          "trace.id" -> Json.fromString(span.context.traceId.show),
          "id" -> Json.fromString(span.context.spanId.show),
          "attributes" ->
            attributesJson(
              span.allAttributes ++ SemanticTags.kindTags(span.kind) ++ SemanticTags
                .statusTags("")(span.status) ++ Map[String, AttributeValue](
                "duration.ms" -> AttributeValue.LongValue(span.end.toEpochMilli - span.start.toEpochMilli),
                "name" -> span.name
              ) ++ span.context.parent.map { parent =>
                "parent.id" -> AttributeValue.StringValue(parent.spanId.show)
              }.toMap
            )
        )
      )
    )

  def toJson(batch: Batch): Json =
    List(JsonObject.fromMap(Map("spans" -> Json.fromValues(batch.spans.map(spanJson))))).asJson
}
