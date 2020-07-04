package io.janstenpickle.trace4cats.opentelemetry.jaeger

import java.util.concurrent.TimeUnit

import cats.effect.IO
import io.janstenpickle.trace4cats.`export`.SemanticTags
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import io.janstenpickle.trace4cats.test.jaeger.BaseJaegerSpec

import scala.concurrent.duration._

class OpenTelemetryJaegerSpanCompleterSpec extends BaseJaegerSpec {
  it should "Send a span to jaeger" in forAll { (span: CompletedSpan, serviceName: String) =>
    val process = TraceProcess(serviceName)

    val updatedSpan = span.copy(
      start = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()),
      end = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis())
    )
    val batch = Batch(process, List(updatedSpan))

    testCompleter(
      OpenTelemetryJaegerSpanCompleter[IO](blocker, process, "localhost", 14250, batchTimeout = 50.millis),
      updatedSpan,
      process,
      batchToJaegerResponse(batch, SemanticTags.kindTags, SemanticTags.statusTags("span."))
    )
  }
}
