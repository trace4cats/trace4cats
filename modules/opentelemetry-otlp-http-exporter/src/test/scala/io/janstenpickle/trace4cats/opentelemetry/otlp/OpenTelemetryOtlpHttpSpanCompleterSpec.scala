package io.janstenpickle.trace4cats.opentelemetry.otlp

import java.time.Instant
import cats.effect.IO
import fs2.Chunk
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, SemanticTags}
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import io.janstenpickle.trace4cats.test.jaeger.BaseJaegerSpec

import scala.concurrent.duration._

class OpenTelemetryOtlpHttpSpanCompleterSpec extends BaseJaegerSpec {
  it should "Send a span to jaeger" in forAll { (span: CompletedSpan.Builder, serviceName: String) =>
    val process = TraceProcess(serviceName)

    val updatedSpan = span.copy(start = Instant.now(), end = Instant.now(), links = span.links)
    val batch = Batch(Chunk(updatedSpan.build(process)))

    testCompleter(
      OpenTelemetryOtlpHttpSpanCompleter
        .blazeClient[IO](process, "localhost", 55681, config = CompleterConfig(batchTimeout = 50.millis)),
      updatedSpan,
      process,
      batchToJaegerResponse(
        batch,
        process,
        SemanticTags.kindTags,
        SemanticTags.statusTags("", statusCode, requireMessage = false),
        processTags,
        additionalTags
      )
    )
  }
}
