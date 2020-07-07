package io.janstenpickle.trace4cats.datadog

import java.math.BigInteger
import java.util.concurrent.TimeUnit

import io.circe.Encoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.janstenpickle.trace4cats.model.{Batch, TraceValue}

// implements https://docs.datadoghq.com/api/v1/tracing/
case class DataDogSpan(
  traceId: BigInteger,
  spanId: BigInteger,
  parentId: Option[BigInteger],
  name: String,
  service: String,
  resource: String,
  meta: Map[String, String],
  metrics: Map[String, Double],
  start: Long,
  duration: Long,
  error: Option[Int]
)

object DataDogSpan {
  def fromBatch(batch: Batch): List[List[DataDogSpan]] =
    batch.spans
      .groupBy(_.context.traceId)
      .values
      .toList
      .map(_.map { span =>
        // IDs use BigIntegers so that they can be unsigned
        val traceId = new BigInteger(1, span.context.traceId.value.drop(8))
        val spanId = new BigInteger(1, span.context.spanId.value)
        val parentId = span.context.parent.map { parent =>
          new BigInteger(1, parent.spanId.value)
        }

        val allAttributes = span.attributes ++ batch.process.attributes

        val startNanos = TimeUnit.MILLISECONDS.toNanos(span.start.toEpochMilli)

        DataDogSpan(
          traceId,
          spanId,
          parentId,
          span.name,
          batch.process.serviceName,
          allAttributes.get("resource.name").fold(batch.process.serviceName)(_.toString),
          allAttributes.collect {
            case (k, TraceValue.StringValue(value)) => k -> value
            case (k, TraceValue.BooleanValue(value)) if k != "error" => k -> value.toString
          },
          allAttributes.collect {
            case (k, TraceValue.DoubleValue(value)) => k -> value
            case (k, TraceValue.LongValue(value)) => k -> value.toDouble
          },
          startNanos,
          TimeUnit.MILLISECONDS.toNanos(span.end.toEpochMilli) - startNanos,
          allAttributes.get("error").map {
            case TraceValue.BooleanValue(true) => 1
            case _ => 0
          }
        )
      })

  implicit val circeConfig: Configuration = Configuration.default.withSnakeCaseMemberNames.withSnakeCaseConstructorNames

  implicit val encoder: Encoder[DataDogSpan] = deriveConfiguredEncoder[DataDogSpan].mapJson(_.dropNullValues)
}
