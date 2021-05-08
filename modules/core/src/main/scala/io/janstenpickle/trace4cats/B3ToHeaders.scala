package io.janstenpickle.trace4cats

import io.janstenpickle.trace4cats.model.{
  Parent,
  SampleDecision,
  SpanContext,
  SpanId,
  TraceFlags,
  TraceHeaders,
  TraceId,
  TraceState
}
import cats.syntax.show._
import org.typelevel.ci.CIString

private[trace4cats] class B3ToHeaders extends ToHeaders {
  final val traceIdHeader = CIString("X-B3-TraceId")
  final val spanIdHeader = CIString("X-B3-SpanId")
  final val parentSpanIdHeader = CIString("X-B3-ParentSpanId")
  final val sampledHeader = CIString("X-B3-Sampled")

  override def toContext(headers: TraceHeaders): Option[SpanContext] =
    (
      headers.values.get(traceIdHeader),
      headers.values.get(spanIdHeader),
      headers.values.get(parentSpanIdHeader),
      headers.values.get(sampledHeader)
    ) match {
      case (Some(traceIdHex), Some(spanIdHex), parentSpanIdHex, sampled) =>
        for {
          traceId <- TraceId.fromHexString(traceIdHex)
          spanId <- SpanId.fromHexString(spanIdHex)
        } yield SpanContext(
          traceId,
          spanId,
          parentSpanIdHex.flatMap { hex =>
            SpanId.fromHexString(hex).map(parent => Parent(parent, isRemote = true))
          },
          TraceFlags(b3SampledFlag(sampled)),
          TraceState.empty,
          isRemote = true
        )
      case _ => None
    }

  override def fromContext(context: SpanContext): TraceHeaders = {
    val sampled = context.traceFlags.sampled match {
      case SampleDecision.Drop => "0"
      case SampleDecision.Include => "1"
    }

    TraceHeaders(
      Map(
        traceIdHeader -> context.traceId.show,
        spanIdHeader -> context.spanId.show,
        sampledHeader -> sampled
      ) ++ context.parent
        .map { parent =>
          parentSpanIdHeader -> parent.spanId.show
        }
    )
  }

}
