package io.janstenpickle.trace4cats.meta

import cats.Eq
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.janstenpickle.trace4cats.`export`.RefSpanCompleter
import trace4cats.kernel.{BuildInfo, SpanSampler}
import trace4cats.model.{AttributeValue, CompletedSpan, MetaTrace, SpanKind, TraceProcess}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class TracedSpanCompleterSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with ArbitraryInstances {

  it should "add a trace for every span when all meta traces are sampled" in forAll {
    (span: CompletedSpan.Builder, completerName: String, process: TraceProcess) =>
      (for {
        completer <- RefSpanCompleter[IO](process)
        tracedCompleter = TracedSpanCompleter[IO](completerName, SpanSampler.always[IO], completer)
        _ <- tracedCompleter.complete(span)
        spans <- completer.get
      } yield {
        lazy val metaSpan = spans.find(_.name == "trace4cats.complete.span").get
        lazy val completedSpan = spans.find(_.name == span.name).get

        spans.size should be(2)

        assert(
          Eq.eqv(
            metaSpan.allAttributes,
            process.attributes ++ Map[String, AttributeValue](
              "completer.name" -> completerName,
              "trace4cats.version" -> BuildInfo.version,
              "service.name" -> process.serviceName
            )
          )
        )
        metaSpan.kind should be(SpanKind.Producer)
        completedSpan.metaTrace should be(Some(MetaTrace(metaSpan.context.traceId, metaSpan.context.spanId)))

      }).unsafeRunSync()
  }

  it should "not add trace for every span when all meta traces are dropped" in forAll {
    (spanBuilder: CompletedSpan.Builder, completerName: String, process: TraceProcess) =>
      val span = spanBuilder.copy(metaTrace = None)

      (for {
        completer <- RefSpanCompleter[IO](process)
        tracedCompleter = TracedSpanCompleter[IO](completerName, SpanSampler.never[IO], completer)
        _ <- tracedCompleter.complete(span)
        spans <- completer.get
      } yield {
        lazy val completedSpan = spans.find(_.name == span.name).get

        spans.size should be(1)
        completedSpan.metaTrace should be(None)

      }).unsafeRunSync()
  }
}
