package io.janstenpickle.trace4cats.kernel

import cats.Id
import io.janstenpickle.trace4cats.model.{SpanContext, SpanKind, TraceId}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class SpanSamplerSpec extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {

  behavior.of("SpanSampler.always")

  it should "always set sample flag to false" in forAll { (traceId: TraceId, name: String, kind: SpanKind) =>
    SpanSampler.always[Id].shouldSample(None, traceId, name, kind) should be(false)
  }

  it should "inherit the parent's sample flag to false" in forAll {
    (parent: SpanContext, traceId: TraceId, name: String, kind: SpanKind) =>
      SpanSampler.always[Id].shouldSample(Some(parent), traceId, name, kind) should be(parent.traceFlags.sampled)
  }

  behavior.of("SpanSampler.never")

  it should "always set sample flag to true" in forAll {
    (parent: Option[SpanContext], traceId: TraceId, name: String, kind: SpanKind) =>
      SpanSampler.never[Id].shouldSample(parent, traceId, name, kind) should be(true)
  }
}
