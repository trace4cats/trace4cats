package io.janstenpickle.trace4cats

import cats.kernel.Eq
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanContext, SpanId, TraceFlags, TraceHeaders, TraceId, TraceState}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class W3cToHeadersSpec extends AnyFlatSpec with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {
  behavior.of("W3cToHeaders")

  val w3c = new W3cToHeaders

  it should "encode and decode span context headers" in forAll { spanContext: SpanContext =>
    assert(
      Eq[Option[SpanContext]]
        .eqv(w3c.toContext(w3c.fromContext(spanContext)), Some(spanContext.copy(parent = None, isRemote = true)))
    )
  }

  it should "decode an example traceparent header" in {
    val headers = TraceHeaders.of("traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")

    val expected = for {
      traceId <- TraceId.fromHexString("4bf92f3577b34da6a3ce929d0e0e4736")
      spanId <- SpanId.fromHexString("00f067aa0ba902b7")
    } yield SpanContext(
      traceId,
      spanId,
      None,
      TraceFlags(sampled = SampleDecision.Include),
      TraceState.empty,
      isRemote = true
    )

    assert(Eq[Option[SpanContext]].eqv(w3c.toContext(headers), expected))
  }

  it should "decode example traceparent and tracestate headers" in {
    val headers = TraceHeaders.of(
      "traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
      "tracestate" -> "rojo=00f067aa0ba902b7,congo=t61rcWkgMzE"
    )

    val expected = for {
      traceId <- TraceId.fromHexString("4bf92f3577b34da6a3ce929d0e0e4736")
      spanId <- SpanId.fromHexString("00f067aa0ba902b7")
      key1 <- TraceState.Key("rojo")
      value1 <- TraceState.Value("00f067aa0ba902b7")
      key2 <- TraceState.Key("congo")
      value2 <- TraceState.Value("t61rcWkgMzE")
      traceState <- TraceState(Map(key1 -> value1, key2 -> value2))
    } yield SpanContext(
      traceId,
      spanId,
      None,
      TraceFlags(sampled = SampleDecision.Include),
      traceState,
      isRemote = true
    )

    assert(Eq[Option[SpanContext]].eqv(w3c.toContext(headers), expected))
  }

  it should "not decode from empty headers" in {
    assert(Eq[Option[SpanContext]].eqv(w3c.toContext(TraceHeaders.empty), None))
  }

  it should "not decode when only tracestate is available" in {
    assert(
      Eq[Option[SpanContext]]
        .eqv(w3c.toContext(TraceHeaders.of("tracestate" -> "rojo=00f067aa0ba902b7,congo=t61rcWkgMzE")), None)
    )
  }
}
