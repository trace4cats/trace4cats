package io.janstenpickle.trace4cats

import cats.syntax.show._
import io.janstenpickle.trace4cats.model._

private[trace4cats] class W3cToHeaders extends ToHeaders {
  final val parentHeader = "traceparent"
  final val stateHeader = "tracestate"

  override def toContext(headers: TraceHeaders): Option[SpanContext] = {
    def splitParent(traceParent: String): Option[(String, String, SampleDecision)] =
      traceParent.split('-').toList match {
        case _ :: traceId :: spanId :: sampled :: Nil =>
          // See: https://www.w3.org/TR/trace-context/#sampled-flag
          if (sampled == "00") Some((traceId, spanId, SampleDecision.Drop))
          else if (sampled == "01") Some((traceId, spanId, SampleDecision.Include))
          else None
        case _ => None
      }

    def stateKv(kv: String): Option[(TraceState.Key, TraceState.Value)] =
      kv.split('=').toList match {
        case k :: v :: Nil =>
          for {
            key <- TraceState.Key(k)
            value <- TraceState.Value(v)
          } yield key -> value
        case _ => None
      }

    val parseState: TraceState = (for {
      state <- headers.values.get(stateHeader)
      split = state.split(',')
      traceState <-
        if (split.length <= 32)
          TraceState(split.flatMap(stateKv).toMap)
        else None
    } yield traceState).getOrElse(TraceState.empty)

    for {
      traceParent <- headers.values.get(parentHeader)
      (tid, sid, sampled) <- splitParent(traceParent)
      traceId <- TraceId.fromHexString(tid)
      spanId <- SpanId.fromHexString(sid)
    } yield SpanContext(traceId, spanId, None, TraceFlags(sampled), parseState, isRemote = true)
  }

  override def fromContext(context: SpanContext): TraceHeaders = {
    // See: https://www.w3.org/TR/trace-context/#sampled-flag
    val sampled = context.traceFlags.sampled match {
      case SampleDecision.Drop => "00"
      case SampleDecision.Include => "01"
    }

    val traceParent = show"00-${context.traceId}-${context.spanId}-$sampled"

    val traceState = context.traceState.values
      .map { case (k, v) => show"$k=$v" }
      .mkString(",")

    TraceHeaders.of(parentHeader -> traceParent, stateHeader -> traceState)
  }
}
