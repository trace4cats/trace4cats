package io.janstenpickle.trace4cats

import cats.syntax.show._
import io.janstenpickle.trace4cats.model._

private[trace4cats] class B3SingleToHeaders extends ToHeaders {
  final val traceHeader = "b3"

  override def toContext(headers: Map[String, String]): Option[SpanContext] =
    headers.get(traceHeader).map(_.split('-').toList) match {
      case Some(traceIdHex :: spanIdHex :: rest) =>
        for {
          traceId <- TraceId.fromHexString(traceIdHex)
          spanId <- SpanId.fromHexString(spanIdHex)
        } yield
          SpanContext(traceId, spanId, rest.lift(1).flatMap { hex =>
            SpanId.fromHexString(hex).map(parent => Parent(parent, isRemote = true))
          }, TraceFlags(b3SampledFlag(rest.headOption)), TraceState.empty, isRemote = true)
      case _ => None
    }

  override def fromContext(context: SpanContext): Map[String, String] = {
    val sampled = context.traceFlags.sampled match {
      case SampleDecision.Drop => "0"
      case SampleDecision.Include => "1"
    }

    val header = show"${context.traceId}-${context.spanId}-$sampled"

    Map(traceHeader -> context.parent.fold(header) { parent =>
      show"$header-${parent.spanId}"
    })
  }

}
