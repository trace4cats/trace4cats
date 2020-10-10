package io.janstenpickle.trace4cats.opentelemetry.otlp

import java.time.Instant

import cats.effect.IO
import io.janstenpickle.trace4cats.`export`.SemanticTags
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import io.janstenpickle.trace4cats.test.jaeger.BaseJaegerSpec

import scala.concurrent.duration._

class OpenTelemetryOtlpHttpSpanCompleterSpec extends BaseJaegerSpec {
  it should "Send a span to jaeger" in forAll { (span: CompletedSpan, serviceName: String) =>
    val process = TraceProcess(serviceName)

    val updatedSpan = span.copy(start = Instant.now(), end = Instant.now())
    val batch = Batch(process, List(updatedSpan))

    testCompleter(
      OpenTelemetryOtlpHttpSpanCompleter
        .blazeClient[IO](blocker, process, "localhost", 55681, batchTimeout = 50.millis),
      updatedSpan,
      process,
      batchToJaegerResponse(
        batch,
        SemanticTags.kindTags,
        SemanticTags.statusTags("", requireMessage = false),
        Map("otlp.instrumentation.library.name" -> "trace4cats")
      ),
    )
  }
}
