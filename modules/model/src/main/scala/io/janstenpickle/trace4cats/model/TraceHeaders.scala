package io.janstenpickle.trace4cats.model

import cats.{Eq, Monoid, Show}
import cats.syntax.contravariant._
import org.typelevel.ci.CIString

case class TraceHeaders(values: Map[CIString, String]) extends AnyVal {
  def ++(that: TraceHeaders): TraceHeaders = new TraceHeaders(this.values ++ that.values)
  def +(elem: (String, String)): TraceHeaders = {
    val (k, v) = elem
    new TraceHeaders(this.values.updated(CIString(k), v))
  }
}

object TraceHeaders {
  def of(zxc: Map[String, String]): TraceHeaders = TraceHeaders(zxc.map { case (k, v) => CIString(k) -> v })
  def of(values: (String, String)*): TraceHeaders = of(values.toMap)
  def ofCi(values: (CIString, String)*): TraceHeaders = TraceHeaders(values.toMap)

  trait Converter[T] {
    def from(t: T): TraceHeaders
    def to(h: TraceHeaders): T
  }

  val empty: TraceHeaders = new TraceHeaders(Map.empty)

  implicit val traceHeadersMonoid: Monoid[TraceHeaders] = Monoid.instance(empty, _ ++ _)
  implicit val traceHeadersShow: Show[TraceHeaders] = Show.catsShowForMap[CIString, String].contramap(_.values)
  implicit val traceHeadersEq: Eq[TraceHeaders] = Eq.catsKernelEqForMap[CIString, String].contramap(_.values)
}
