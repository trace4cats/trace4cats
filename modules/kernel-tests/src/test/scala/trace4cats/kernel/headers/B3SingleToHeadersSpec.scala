package trace4cats.kernel.headers

import cats.Eq
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import trace4cats.model._
import trace4cats.test.ArbitraryInstances

class B3SingleToHeadersSpec extends AnyFlatSpec with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {
  behavior.of("B3SingleToHeaders")

  val b3Single = new B3SingleToHeaders

  it should "encode and decode span context headers" in forAll { (spanContext: SpanContext) =>
    assert(
      Eq.eqv(
        b3Single.toContext(b3Single.fromContext(spanContext)),
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
    val headers = TraceHeaders.of("b3" -> "80f198ee56343ba864fe8b2a57d3eff7-e457b5a2e4d86bd1-1-05e3ac9a4f6e3b90")

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

    assert(Eq.eqv(b3Single.toContext(headers), expected))
  }
}
