package io.janstenpickle.trace4cats.jaeger

import java.util.concurrent.TimeUnit

import cats.effect.IO
import io.janstenpickle.trace4cats.`export`.SemanticTags
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import io.janstenpickle.trace4cats.test.jaeger.BaseJaegerSpec

import scala.concurrent.duration._

class JaegerSpanCompleterSpec extends BaseJaegerSpec {
  it should "Send a span to jaeger" in forAll { (span: CompletedSpan, process: TraceProcess) =>
    val updatedSpan = span.copy(
      start = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()),
      end = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis())
    )
    val batch = Batch(process, List(updatedSpan))

    testCompleter(
      JaegerSpanCompleter[IO](blocker, process, "localhost", 6831, batchTimeout = 50.millis),
      updatedSpan,
      process,
      batchToJaegerResponse(batch, SemanticTags.kindTags, SemanticTags.statusTags("span."))
    )
  }
}
