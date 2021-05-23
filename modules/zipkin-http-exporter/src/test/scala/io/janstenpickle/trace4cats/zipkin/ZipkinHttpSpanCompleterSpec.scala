package io.janstenpickle.trace4cats.zipkin

import cats.effect.IO
import fs2.Chunk
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, SemanticTags}
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import io.janstenpickle.trace4cats.test.jaeger.BaseJaegerSpec

import java.time.Instant
import scala.concurrent.duration._

class ZipkinHttpSpanCompleterSpec extends BaseJaegerSpec {
  it should "Send a span to Zipkin" in forAll { (span: CompletedSpan.Builder, serviceName: String) =>
    val process = TraceProcess(serviceName)

    val updatedSpan = span.copy(
      start = Instant.now(),
      end = Instant.now(),
      attributes = span.attributes.filterNot { case (key, _) =>
        excludedTagKeys.contains(key)
      }
    )
    val batch = Batch(Chunk(updatedSpan.build(process)))

    testCompleter(
      ZipkinHttpSpanCompleter
        .blazeClient[IO](process, "localhost", 9411, config = CompleterConfig(batchTimeout = 50.millis)),
      updatedSpan,
      process,
      batchToJaegerResponse(
        batch,
        process,
        kindToAttributes,
        SemanticTags.statusTags(prefix = "", requireMessage = false),
        processToAttributes,
        convertAttributes = convertAttributes,
        internalSpanFormat = "zipkin",
        followsFrom = false
      )
    )
  }
}
