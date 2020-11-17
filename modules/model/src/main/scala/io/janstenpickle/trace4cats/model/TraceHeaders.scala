package io.janstenpickle.trace4cats.model

import cats.{Eq, Monoid, Show}
import cats.syntax.contravariant._

case class TraceHeaders(values: Map[String, String]) extends AnyVal {
  def +(that: TraceHeaders): TraceHeaders = TraceHeaders(this.values ++ that.values)
}

object TraceHeaders {
  def of(values: (String, String)*): TraceHeaders = TraceHeaders(values.toMap)

  val empty: TraceHeaders = TraceHeaders(Map.empty)

  implicit val traceHeadersMonoid: Monoid[TraceHeaders] = Monoid.instance(empty, _ + _)
  implicit val traceHeadersShow: Show[TraceHeaders] = Show.catsShowForMap[String, String].contramap(_.values)
  implicit val traceHeadersEq: Eq[TraceHeaders] = Eq.catsKernelEqForMap[String, String].contramap(_.values)
}
