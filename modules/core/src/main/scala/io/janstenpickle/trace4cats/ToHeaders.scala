package io.janstenpickle.trace4cats

import cats.kernel.Semigroup
import cats.syntax.semigroup._
import io.janstenpickle.trace4cats.model._

trait ToHeaders {
  def toContext(headers: Map[String, String]): Option[SpanContext]
  def fromContext(context: SpanContext): Map[String, String]
}

object ToHeaders {
  implicit val toHeadersSemigroup: Semigroup[ToHeaders] = new Semigroup[ToHeaders] {
    override def combine(x: ToHeaders, y: ToHeaders): ToHeaders = new ToHeaders {
      override def toContext(headers: Map[String, String]): Option[SpanContext] =
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

      override def fromContext(context: SpanContext): Map[String, String] =
        x.fromContext(context) ++ y.fromContext(context)
    }
  }

  val w3c: ToHeaders = new W3cToHeaders()
  val b3: ToHeaders = new B3ToHeaders()
  val b3Single: ToHeaders = new B3SingleToHeaders
  val envoy: ToHeaders = new EnvoyToHeaders
  val all: ToHeaders = w3c |+| b3 |+| b3Single |+| envoy
}
