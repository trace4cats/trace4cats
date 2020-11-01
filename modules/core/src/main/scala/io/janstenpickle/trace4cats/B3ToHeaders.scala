package io.janstenpickle.trace4cats

import io.janstenpickle.trace4cats.model.{Parent, SampleDecision, SpanContext, SpanId, TraceFlags, TraceId, TraceState}
import cats.syntax.show._

private[trace4cats] class B3ToHeaders extends ToHeaders {
  final val traceIdHeader = "X-B3-TraceId"
  final val spanIdHeader = "X-B3-SpanId"
  final val parentSpanIdHeader = "X-B3-ParentSpanId"
  final val sampledHeader = "X-B3-Sampled"

  override def toContext(headers: Map[String, String]): Option[SpanContext] =
    (
      headers.get(traceIdHeader),
      headers.get(spanIdHeader),
      headers.get(parentSpanIdHeader),
      headers.get(sampledHeader)
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

  override def fromContext(context: SpanContext): Map[String, String] = {
    val sampled = context.traceFlags.sampled match {
      case SampleDecision.Drop => "0"
      case SampleDecision.Include => "1"
    }

    Map(
      traceIdHeader -> context.traceId.show,
      spanIdHeader -> context.spanId.show,
      sampledHeader -> sampled
    ) ++ context.parent
      .map { parent =>
        parentSpanIdHeader -> parent.spanId.show
      }
  }

}
