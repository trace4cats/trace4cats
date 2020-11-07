package io.janstenpickle.trace4cats

import cats.Eq
import io.janstenpickle.trace4cats.model.{Parent, SampleDecision, SpanContext, SpanId, TraceFlags, TraceId, TraceState}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class B3ToHeadersSpec extends AnyFlatSpec with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {
  behavior.of("B3ToHeaders")

  val b3 = new B3ToHeaders

  it should "encode and decode span context headers" in forAll { spanContext: SpanContext =>
    assert(
      Eq.eqv(
        b3.toContext(b3.fromContext(spanContext)),
        Some(
          spanContext.copy(
            parent = spanContext.parent.map(_.copy(isRemote = true)),
            traceState = TraceState.empty,
            isRemote = true
          )
        )
      )
    )
  }

  it should "decode example B3 headers" in {
    val headers = Map(
      "X-B3-TraceId" -> "80f198ee56343ba864fe8b2a57d3eff7",
      "X-B3-ParentSpanId" -> "05e3ac9a4f6e3b90",
      "X-B3-SpanId" -> "e457b5a2e4d86bd1",
      "X-B3-Sampled" -> "1"
    )

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

    assert(Eq.eqv(b3.toContext(headers), expected))
  }
}
