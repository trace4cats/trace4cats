package io.janstenpickle.trace4cats.jaeger

import java.util.concurrent.TimeUnit

import cats.effect.IO
import io.janstenpickle.trace4cats.`export`.SemanticTags
import io.janstenpickle.trace4cats.model.Batch
import io.janstenpickle.trace4cats.test.jaeger.BaseJaegerSpec

class JaegerSpanExporterSpec extends BaseJaegerSpec {
  it should "Send a batch of spans to jaeger" in forAll { batch: Batch =>
    val updatedBatch =
      Batch(
        batch.process,
        batch.spans.map(
          _.copy(
            start = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()),
            end = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis())
          )
        )
      )

    testExporter(
      JaegerSpanExporter[IO](blocker, "localhost", 6831),
      updatedBatch,
      batchToJaegerResponse(updatedBatch, SemanticTags.kindTags, SemanticTags.statusTags("span."))
    )
  }
}
