package io.janstenpickle.trace4cats.kernel

import java.nio.ByteBuffer

import cats.Id
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanContext, SpanKind, TraceId}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class SpanSamplerSpec extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {

  behavior.of("SpanSampler.always")

  it should "always set sample flag to false" in forAll { (traceId: TraceId, name: String, kind: SpanKind) =>
    SpanSampler.always[Id].shouldSample(None, traceId, name, kind) should be(SampleDecision.Include)
  }

  it should "inherit the parent's sample flag" in forAll {
    (parent: SpanContext, traceId: TraceId, name: String, kind: SpanKind) =>
      SpanSampler.always[Id].shouldSample(Some(parent), traceId, name, kind) should be(parent.traceFlags.sampled)
  }

  behavior.of("SpanSampler.never")

  it should "always set sample flag to true" in forAll {
    (parent: Option[SpanContext], traceId: TraceId, name: String, kind: SpanKind) =>
      SpanSampler.never[Id].shouldSample(parent, traceId, name, kind) should be(SampleDecision.Drop)
  }

  behavior.of("SpanSampler.probabilistic")

  it should "inherit the parent's sample flag" in forAll {
    (parent: SpanContext, traceId: TraceId, name: String, kind: SpanKind) =>
      SpanSampler.probabilistic[Id](1.0).shouldSample(Some(parent), traceId, name, kind) should be(
        parent.traceFlags.sampled
      )
  }

  it should "apply to root spans only" in forAll { (traceId: TraceId, name: String, kind: SpanKind) =>
    SpanSampler.probabilistic[Id](0.0).shouldSample(None, traceId, name, kind) should be(SampleDecision.Drop)
    SpanSampler.probabilistic[Id](1.0).shouldSample(None, traceId, name, kind) should be(SampleDecision.Include)
  }

  it should "not apply to child spans" in forAll {
    (parent: SpanContext, traceId: TraceId, name: String, kind: SpanKind) =>
      val p = parent.copy(traceFlags = parent.traceFlags.copy(sampled = SampleDecision.Include))

      SpanSampler.probabilistic[Id](0.0).shouldSample(Some(p), traceId, name, kind) should be(SampleDecision.Include)
      SpanSampler.probabilistic[Id](1.0).shouldSample(Some(p), traceId, name, kind) should be(SampleDecision.Include)
  }

  it should "apply to child spans when configured to do so" in forAll {
    (parent: SpanContext, traceId: TraceId, name: String, kind: SpanKind) =>
      val p = parent.copy(traceFlags = parent.traceFlags.copy(sampled = SampleDecision.Include))

      SpanSampler.probabilistic[Id](0.0, rootSpansOnly = false).shouldSample(Some(p), traceId, name, kind) should be(
        SampleDecision.Drop
      )
      SpanSampler.probabilistic[Id](1.0, rootSpansOnly = false).shouldSample(Some(p), traceId, name, kind) should be(
        SampleDecision.Include
      )
  }

  it should "always set sample flag to true when probability is 0.0" in forAll {
    (traceId: TraceId, name: String, kind: SpanKind) =>
      SpanSampler.probabilistic[Id](0.0).shouldSample(None, traceId, name, kind) should be(SampleDecision.Drop)
  }

  it should "always set sample flag to false when probability is 1.0" in forAll {
    (traceId: TraceId, name: String, kind: SpanKind) =>
      SpanSampler.probabilistic[Id](1.0).shouldSample(None, traceId, name, kind) should be(SampleDecision.Include)
  }

  it should "sample if ID lo bytes are greater than probability boundary" in forAll {
    (traceId: TraceId, name: String, kind: SpanKind) =>
      val lo = Long.MaxValue - 2

      val updatedId = TraceId(traceId.value.dropRight(8) ++ ByteBuffer.allocate(8).putLong(lo).array()).get

      SpanSampler.probabilistic[Id](0.05).shouldSample(None, updatedId, name, kind) should be(SampleDecision.Drop)
  }

  it should "not sample if ID lo bytes are lower than probability boundary" in forAll {
    (traceId: TraceId, name: String, kind: SpanKind) =>
      val lo = Long.MinValue + 2

      val updatedId = TraceId(traceId.value.dropRight(8) ++ ByteBuffer.allocate(8).putLong(lo).array()).get

      SpanSampler.probabilistic[Id](0.05).shouldSample(None, updatedId, name, kind) should be(SampleDecision.Include)
  }
}
