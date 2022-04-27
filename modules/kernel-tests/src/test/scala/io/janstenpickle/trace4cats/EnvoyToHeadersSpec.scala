package io.janstenpickle.trace4cats

import java.util.UUID

import cats.Eq
import io.janstenpickle.trace4cats.model._
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class EnvoyToHeadersSpec extends AnyFlatSpec with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {
  behavior.of("EnvoyToHeaders")

  val envoy = new EnvoyToHeaders

  it should "encode and decode span context headers" in forAll { (spanContext: SpanContext) =>
    assert(
      Eq.eqv(
        envoy.toContext(envoy.fromContext(spanContext)),
        Some(
          spanContext.copy(
            parent = spanContext.parent.map(_.copy(isRemote = true)),
            traceState = TraceState.empty,
            traceFlags = TraceFlags(sampled = SampleDecision.Include),
            isRemote = true
          )
        )
      )
    )
  }

  it should "decode example Envoy headers" in {
    val headers =
      TraceHeaders.of("x-ot-span-context" -> "80f198ee56343ba864fe8b2a57d3eff7;e457b5a2e4d86bd1;05e3ac9a4f6e3b90;cs")

    val expected = for {
      traceId <- TraceId.fromHexString("80f198ee56343ba864fe8b2a57d3eff7")
      spanId <- SpanId.fromHexString("e457b5a2e4d86bd1")
      parentSpanId <- SpanId.fromHexString("05e3ac9a4f6e3b90")
    } yield SpanContext(
      traceId,
      spanId,
      Some(Parent(parentSpanId, isRemote = true)),
      TraceFlags(sampled = SampleDecision.Include),
      TraceState.empty,
      isRemote = true
    )

    assert(Eq.eqv(envoy.toContext(headers), expected))
  }

  it should "decode example Envoy headers with no parent" in {
    val headers =
      TraceHeaders.of("x-ot-span-context" -> "80f198ee56343ba864fe8b2a57d3eff7;e457b5a2e4d86bd1;0000000000000000;cs")

    val expected = for {
      traceId <- TraceId.fromHexString("80f198ee56343ba864fe8b2a57d3eff7")
      spanId <- SpanId.fromHexString("e457b5a2e4d86bd1")
    } yield SpanContext(
      traceId,
      spanId,
      None,
      TraceFlags(sampled = SampleDecision.Include),
      TraceState.empty,
      isRemote = true
    )

    assert(Eq.eqv(envoy.toContext(headers), expected))
  }

  it should "decode and encode example Envoy headers, passing through the envoy request ID" in {
    val reqId = UUID.randomUUID().toString
    val headers = TraceHeaders.of(
      "x-request-id" -> reqId,
      "x-ot-span-context" -> "80f198ee56343ba864fe8b2a57d3eff7;e457b5a2e4d86bd1;0000000000000000;cs"
    )

    val expected = for {
      traceId <- TraceId.fromHexString("80f198ee56343ba864fe8b2a57d3eff7")
      spanId <- SpanId.fromHexString("e457b5a2e4d86bd1")
    } yield SpanContext(
      traceId,
      spanId,
      None,
      TraceFlags(sampled = SampleDecision.Include),
      TraceState(Map(TraceState.Key.unsafe("envoy-request-id") -> TraceState.Value.unsafe(reqId))).get,
      isRemote = true
    )

    assert(Eq.eqv(envoy.toContext(headers), expected))
    assert(Eq.eqv(envoy.fromContext(expected.get), headers))
  }
}
