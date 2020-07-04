package io.janstenpickle.trace4cats.opentelemetry.jaeger

import java.util.concurrent.TimeUnit

import cats.effect.IO
import io.janstenpickle.trace4cats.`export`.SemanticTags
import io.janstenpickle.trace4cats.model.Batch
import io.janstenpickle.trace4cats.test.jaeger.BaseJaegerSpec

class OpenTelemetryJaegerSpanExporterSpec extends BaseJaegerSpec {
  it should "Send a batch of spans to jaeger" in forAll { batch: Batch =>
    val updatedBatch =
      Batch(
        batch.process.copy(attributes = Map.empty),
        batch.spans.map(
          _.copy(
            start = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()),
            end = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis())
          )
        )
      )

    testExporter(
      OpenTelemetryJaegerSpanExporter[IO](blocker, "localhost", 14250),
      updatedBatch,
      batchToJaegerResponse(updatedBatch, SemanticTags.kindTags, SemanticTags.statusTags("span."))
    )
  }
}
