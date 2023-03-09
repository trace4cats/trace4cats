package trace4cats.kernel.headers

import cats.Eq
import cats.implicits._
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import trace4cats.model._
import trace4cats.test.ArbitraryInstances

class GoogleCloudTraceToHeadersSpec
    extends AnyFlatSpec
    with ScalaCheckDrivenPropertyChecks
    with TableDrivenPropertyChecks
    with ArbitraryInstances
    with OptionValues {

  behavior.of("GoogleCloudTraceToHeaders")

  val cloudTrace = new GoogleCloudTraceToHeaders

  it should "encode and decode span context headers" in forAll { (spanContext: SpanContext) =>
    assert(
      Eq.eqv(
        cloudTrace.toContext(cloudTrace.fromContext(spanContext)),
        Some(spanContext.copy(parent = none, traceState = TraceState.empty, isRemote = true))
      )
    )
  }

  it should "parse header strings" in forAll { (traceId: TraceId, spanId: SpanId, enabled: Boolean) =>
    val spanIdInt = GoogleCloudTraceToHeaders.spanIdAsBigInt(spanId)
    val header = show"$traceId/$spanIdInt;o=${if (enabled) "1" else "0"}"
    val parsed = GoogleCloudTraceToHeaders.parse(header).toOption.value

    assert(Eq.eqv(parsed.traceId, traceId))
    assert(Eq.eqv(parsed.spanId, spanId))

    if (enabled) assert(Eq.eqv(parsed.traceFlags.sampled, SampleDecision.Include))
    else assert(Eq.eqv(parsed.traceFlags.sampled, SampleDecision.Drop))
  }

  it should "decode example Cloud Trace headers" in {
    val spanId = 2205310701640571284L
    val headers = TraceHeaders.of("X-Cloud-Trace-Context" -> s"105445aa7843bc8bf206b12000100000/$spanId;o=1")

    val expected = for {
      traceId <- TraceId.fromHexString("105445aa7843bc8bf206b12000100000")
      spanId <- SpanId.fromHexString(spanId.toHexString)
    } yield SpanContext(
      traceId,
      spanId,
      None,
      TraceFlags(sampled = SampleDecision.Include),
      TraceState.empty,
      isRemote = true
    )

    assert(cloudTrace.toContext(headers).isDefined)
    assert(Eq.eqv(cloudTrace.toContext(headers), expected))
  }

  it should "decode example Cloud Trace headers without the `;o=` part" in {
    val spanId = 2205310701640571284L
    val headers = TraceHeaders.of("X-Cloud-Trace-Context" -> s"105445aa7843bc8bf206b12000100000/$spanId")

    val expected = for {
      traceId <- TraceId.fromHexString("105445aa7843bc8bf206b12000100000")
      spanId <- SpanId.fromHexString(spanId.toHexString)
    } yield SpanContext(
      traceId,
      spanId,
      None,
      TraceFlags(sampled = SampleDecision.Include),
      TraceState.empty,
      isRemote = true
    )

    assert(cloudTrace.toContext(headers).isDefined)
    assert(Eq.eqv(cloudTrace.toContext(headers), expected))
  }
}
