package trace4cats.model

import trace4cats.kernel.ErrorHandler

case class SpanParams(spanName: String, spanKind: SpanKind, traceHeaders: TraceHeaders, errorHandler: ErrorHandler)

object SpanParams {
  implicit def fromTuple(t: (String, SpanKind, TraceHeaders, ErrorHandler)): SpanParams =
    SpanParams(t._1, t._2, t._3, t._4)
}
