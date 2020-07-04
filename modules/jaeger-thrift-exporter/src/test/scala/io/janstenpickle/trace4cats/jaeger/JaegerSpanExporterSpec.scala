package io.janstenpickle.trace4cats.jaeger

import java.time.Instant

import cats.effect.IO
import io.janstenpickle.trace4cats.`export`.SemanticTags
import io.janstenpickle.trace4cats.model.Batch
import io.janstenpickle.trace4cats.test.jaeger.BaseJaegerSpec

class JaegerSpanExporterSpec extends BaseJaegerSpec {
  it should "Send a batch of spans to jaeger" in forAll { batch: Batch =>
    val updatedBatch =
      Batch(batch.process, batch.spans.map(_.copy(start = Instant.now(), end = Instant.now())))

    testExporter(
      JaegerSpanExporter[IO](blocker, "localhost", 6831),
      updatedBatch,
      batchToJaegerResponse(updatedBatch, SemanticTags.kindTags, SemanticTags.statusTags("span."))
    )
  }
}
