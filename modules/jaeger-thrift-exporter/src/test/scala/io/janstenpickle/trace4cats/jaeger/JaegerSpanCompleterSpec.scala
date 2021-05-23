package io.janstenpickle.trace4cats.jaeger

import java.time.Instant
import cats.effect.IO
import fs2.Chunk
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, SemanticTags}
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import io.janstenpickle.trace4cats.test.jaeger.BaseJaegerSpec

import scala.concurrent.duration._

class JaegerSpanCompleterSpec extends BaseJaegerSpec {
  it should "Send a span to jaeger" in forAll { (span: CompletedSpan.Builder, process: TraceProcess) =>
    val updatedSpan = span.copy(
      start = Instant.now(),
      end = Instant.now(),
      attributes = span.attributes
        .filterNot { case (key, _) =>
          excludedTagKeys.contains(key)
        }
    )
    val batch = Batch(Chunk(updatedSpan.build(process)))

    testCompleter(
      JaegerSpanCompleter[IO](process, "localhost", 6831, config = CompleterConfig(batchTimeout = 50.millis)),
      updatedSpan,
      process,
      batchToJaegerResponse(
        batch,
        process,
        SemanticTags.kindTags,
        SemanticTags.statusTags("span."),
        SemanticTags.processTags
      )
    )
  }
}
