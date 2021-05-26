package io.janstenpickle.trace4cats.opentelemetry.jaeger

import java.time.Instant
import cats.effect.IO
import fs2.Chunk
import io.janstenpickle.trace4cats.`export`.CompleterConfig
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import io.janstenpickle.trace4cats.test.jaeger.BaseJaegerSpec

import scala.concurrent.duration._

class OpenTelemetryJaegerSpanCompleterSpec extends BaseJaegerSpec {
  it should "Send a span to jaeger" in forAll { (span: CompletedSpan.Builder, serviceName: String) =>
    val process = TraceProcess(serviceName)

    val updatedSpan = span.copy(
      start = Instant.now(),
      end = Instant.now(),
      attributes = span.attributes.filterNot { case (key, _) =>
        excludedTagKeys.contains(key)
      },
    )
    val batch = Batch(Chunk(updatedSpan.build(process)))

    testCompleter(
      OpenTelemetryJaegerSpanCompleter[IO](
        process,
        "localhost",
        14250,
        config = CompleterConfig(batchTimeout = 50.millis)
      ),
      updatedSpan,
      process,
      batchToJaegerResponse(batch, process, kindTags, statusTags, processTags, additionalTags)
    )
  }
}
