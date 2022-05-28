package trace4cats.kernel

import cats.kernel.Semigroup
import cats.syntax.semigroup._
import trace4cats.kernel.headers.{
  B3SingleToHeaders,
  B3ToHeaders,
  EnvoyToHeaders,
  GoogleCloudTraceToHeaders,
  W3cToHeaders
}
import trace4cats.model._

trait ToHeaders {
  def toContext(headers: TraceHeaders): Option[SpanContext]
  def fromContext(context: SpanContext): TraceHeaders
}

object ToHeaders {
  implicit val toHeadersSemigroup: Semigroup[ToHeaders] = new Semigroup[ToHeaders] {
    override def combine(x: ToHeaders, y: ToHeaders): ToHeaders =
      new ToHeaders {
        override def toContext(headers: TraceHeaders): Option[SpanContext] =
          (x.toContext(headers), y.toContext(headers)) match {
            case (ctx @ Some(_), None) => ctx
            case (None, ctx @ Some(_)) => ctx
            // when multiple headers are present, combine the spans
            case (Some(x0), Some(y0)) =>
              // certain implementations do not send a parent span ID, this takes the first available
              val parent = x0.parent.orElse(y0.parent)
              // some implementation may include trace state, this combines all states
              // assuming it doesn't exceed the element limit
              val state = TraceState(x0.traceState.values ++ y0.traceState.values).getOrElse(x0.traceState)

              Some(x0.copy(parent = parent, traceState = state))
            case (None, None) => None
          }

        override def fromContext(context: SpanContext): TraceHeaders =
          TraceHeaders(x.fromContext(context).values ++ y.fromContext(context).values)
      }
  }

  val w3c: ToHeaders = new W3cToHeaders
  val b3: ToHeaders = new B3ToHeaders
  val b3Single: ToHeaders = new B3SingleToHeaders
  val envoy: ToHeaders = new EnvoyToHeaders
  val googleCloudTrace: ToHeaders = new GoogleCloudTraceToHeaders

  /** Convert trace context to/from open standard trace headers
    */
  val standard: ToHeaders = w3c |+| b3 |+| b3Single

  /** Convert trace context to/from open standard and non-standard trace headers
    */
  val all: ToHeaders = standard |+| envoy |+| googleCloudTrace
}
