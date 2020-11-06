package io.janstenpickle.trace4cats.opentelemetry.otlp

import java.time.Instant

import cats.effect.IO
import io.janstenpickle.trace4cats.`export`.SemanticTags
import io.janstenpickle.trace4cats.model.{Batch, TraceProcess}
import io.janstenpickle.trace4cats.test.jaeger.BaseJaegerSpec

class OpenTelemetryOtlpGrpcSpanExporterSpec extends BaseJaegerSpec {
  it should "Send a batch of spans to jaeger" in forAll { (batch: Batch, process: TraceProcess) =>
    val updatedBatch =
      Batch(
        batch.spans.map(
          span =>
            span.copy(
              serviceName = process.serviceName,
              attributes = process.attributes ++ span.attributes,
              start = Instant.now(),
              end = Instant.now()
            )
        )
      )

    testExporter(
      OpenTelemetryOtlpGrpcSpanExporter[IO]("localhost", 55680),
      updatedBatch,
      batchToJaegerResponse(
        updatedBatch,
        process,
        SemanticTags.kindTags,
        SemanticTags.statusTags("", statusCode, requireMessage = false),
        Map("otlp.instrumentation.library.name" -> "trace4cats")
      ),
      checkProcess = false
    )
  }
}
