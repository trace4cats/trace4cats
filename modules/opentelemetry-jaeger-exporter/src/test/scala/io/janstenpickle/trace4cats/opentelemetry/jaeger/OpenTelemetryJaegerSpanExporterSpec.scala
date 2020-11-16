package io.janstenpickle.trace4cats.opentelemetry.jaeger

import java.time.Instant

import cats.data.NonEmptyList
import cats.effect.IO
import fs2.Chunk
import io.janstenpickle.trace4cats.`export`.SemanticTags
import io.janstenpickle.trace4cats.model.{Batch, Link, TraceProcess}
import io.janstenpickle.trace4cats.test.jaeger.BaseJaegerSpec

class OpenTelemetryJaegerSpanExporterSpec extends BaseJaegerSpec {
  it should "Send a batch of spans to jaeger" in forAll { (batch: Batch[Chunk], process: TraceProcess) =>
    val updatedBatch =
      Batch(
        batch.spans.map(span =>
          span.copy(
            serviceName = process.serviceName,
            attributes = process.attributes ++ span.allAttributes,
            links = span.links
              .flatMap(links => NonEmptyList.fromList(links.collect { case l @ Link.Parent(_, _) => l })),
            start = Instant.now(),
            end = Instant.now()
          )
        )
      )

    testExporter(
      OpenTelemetryJaegerSpanExporter[IO, Chunk]("localhost", 14250, process.serviceName),
      updatedBatch,
      batchToJaegerResponse(
        updatedBatch,
        process,
        SemanticTags.kindTags,
        SemanticTags.statusTags("span.", statusCode),
        Map("otel.library.name" -> "trace4cats")
      ),
      checkProcess = false
    )
  }
}
