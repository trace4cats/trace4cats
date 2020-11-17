package io.janstenpickle.trace4cats

import cats.Eq
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

private[trace4cats] class EnvoyToHeaders extends ToHeaders {
  final val requestIdHeader = "x-request-id"
  final val requestIdStateKey = TraceState.Key.unsafe("envoy-request-id")
  final val clientTraceIdHeader = "x-client-trace-id"
  final val contextHeader = "x-ot-span-context"

  override def toContext(headers: TraceHeaders): Option[SpanContext] = {
    val traceState =
      (for {
        reqId <- headers.values.get(clientTraceIdHeader).orElse(headers.values.get(requestIdHeader))
        reqIdTraceState <- TraceState.Value(reqId)
        state <- TraceState(Map(requestIdStateKey -> reqIdTraceState))
      } yield state).getOrElse(TraceState.empty)

    headers.values.get(contextHeader).map(_.split(';').toList) match {
      case Some(traceIdHex :: spanIdHex :: parentSpanIdHex :: _ :: Nil) =>
        for {
          traceId <- TraceId.fromHexString(traceIdHex)
          spanId <- SpanId.fromHexString(spanIdHex)
          parentSpanId <- SpanId.fromHexString(parentSpanIdHex)
          parent = if (Eq.eqv(parentSpanId, SpanId.invalid)) None else Some(Parent(parentSpanId, isRemote = true))
        } yield SpanContext(
          traceId,
          spanId,
          parent,
          TraceFlags(sampled = SampleDecision.Include),
          traceState,
          isRemote = true
        )
      case _ => None
    }
  }

  override def fromContext(context: SpanContext): TraceHeaders =
    TraceHeaders(
      Map(
        contextHeader -> show"${context.traceId.show};${context.spanId.show};${context.parent.fold(SpanId.invalid)(_.spanId).show};cs"
      ) ++ context.traceState.values.get(requestIdStateKey).map(traceValue => requestIdHeader -> traceValue.v)
    )
}
