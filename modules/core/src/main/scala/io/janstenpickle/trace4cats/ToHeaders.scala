package io.janstenpickle.trace4cats

import cats.implicits._
import io.janstenpickle.trace4cats.model._

trait ToHeaders[F[_]] {
  def toContext(headers: Map[String, String]): Option[SpanContext]
  def fromContext(context: SpanContext): Map[String, String]
}

object ToHeaders {

  def apply[F[_]](implicit toHeaders: ToHeaders[F]): ToHeaders[F] = toHeaders

  implicit def http[F[_]]: ToHeaders[F] = new ToHeaders[F] {
    final val parentHeader = "traceparent"
    final val stateHeader = "tracestate"

    override def toContext(headers: Map[String, String]): Option[SpanContext] = {
      def splitParent(traceParent: String): Option[(String, String, Boolean)] =
        traceParent.split('-').toList match {
          case _ :: traceId :: spanId :: sampled :: Nil =>
            if (sampled == "01") Some((traceId, spanId, true))
            else if (sampled == "00") Some((traceId, spanId, false))
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
        state <- headers.get(stateHeader)
        split = state.split(',')
        traceState <- if (split.length <= 32)
          TraceState(split.flatMap(stateKv).toMap)
        else None
      } yield traceState).getOrElse(TraceState.empty)

      for {
        traceParent <- headers.get(parentHeader)
        (tid, sid, sampled) <- splitParent(traceParent)
        traceId <- TraceId.fromHexString(tid)
        spanId <- SpanId.fromHexString(sid)
      } yield SpanContext(traceId, spanId, None, TraceFlags(sampled), parseState, isRemote = true)
    }

    override def fromContext(context: SpanContext): Map[String, String] = {
      val sampled = if (context.traceFlags.sampled) "01" else "00"
      val traceParent = show"00-${context.traceId}-${context.spanId}-$sampled"

      val traceState = context.traceState.values
        .map { case (k, v) => show"$k=$v" }
        .mkString(",")

      Map(parentHeader -> traceParent, stateHeader -> traceState)
    }
  }
}
