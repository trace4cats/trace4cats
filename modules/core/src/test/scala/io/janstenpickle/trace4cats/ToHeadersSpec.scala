package io.janstenpickle.trace4cats

import cats.kernel.Eq
import io.janstenpickle.trace4cats.model.{SpanContext, SpanId, TraceFlags, TraceId, TraceState}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class ToHeadersSpec extends AnyFlatSpec with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {
  behavior.of("ToHeaders.w3c")

  it should "encode and decode span context headers" in forAll { spanContext: SpanContext =>
    assert(
      Eq[Option[SpanContext]]
        .eqv(
          ToHeaders.w3c.toContext(ToHeaders.w3c.fromContext(spanContext)),
          Some(spanContext.copy(parent = None, isRemote = true))
        )
    )
  }

  it should "decode an example traceparent header" in {
    val headers = Map("traceparent" -> "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")

    val expected = for {
      traceId <- TraceId.fromHexString("4bf92f3577b34da6a3ce929d0e0e4736")
      spanId <- SpanId.fromHexString("00f067aa0ba902b7")
    } yield SpanContext(traceId, spanId, None, TraceFlags(sampled = false), TraceState.empty, isRemote = true)

    assert(Eq[Option[SpanContext]].eqv(ToHeaders.w3c.toContext(headers), expected))
  }

  it should "decode example traceparent and tracestate headers" in {
    val headers = Map(
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
    } yield SpanContext(traceId, spanId, None, TraceFlags(sampled = false), traceState, isRemote = true)

    assert(Eq[Option[SpanContext]].eqv(ToHeaders.w3c.toContext(headers), expected))
  }

  it should "not decode from empty headers" in {
    assert(Eq[Option[SpanContext]].eqv(ToHeaders.w3c.toContext(Map.empty), None))
  }

  it should "not decode when only tracestate is available" in {
    assert(
      Eq[Option[SpanContext]]
        .eqv(ToHeaders.w3c.toContext(Map("tracestate" -> "rojo=00f067aa0ba902b7,congo=t61rcWkgMzE")), None)
    )
  }
}
