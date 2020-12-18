package io.janstenpickle.trace4cats.opentelemetry.otlp

import java.time.Instant

import cats.effect.IO
import fs2.Chunk
import io.janstenpickle.trace4cats.`export`.SemanticTags
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import io.janstenpickle.trace4cats.test.jaeger.BaseJaegerSpec

import scala.concurrent.duration._

class OpenTelemetryOtlpGrpcSpanCompleterSpec extends BaseJaegerSpec {
  it should "Send a span to jaeger" in forAll { (span: CompletedSpan.Builder, serviceName: String) =>
    val process = TraceProcess(serviceName)

    val updatedSpan = span.copy(start = Instant.now(), end = Instant.now(), links = span.links)
    val batch = Batch(Chunk(updatedSpan.build(serviceName)))

    testCompleter(
      OpenTelemetryOtlpGrpcSpanCompleter[IO](process, "localhost", 55680, batchTimeout = 50.millis),
      updatedSpan,
      process,
      batchToJaegerResponse(
        batch,
        TraceProcess(serviceName),
        SemanticTags.kindTags,
        SemanticTags.statusTags("", statusCode, requireMessage = false),
        Map("otlp.instrumentation.library.name" -> "trace4cats")
      ),
      checkProcess = false
    )
  }
}
