package trace4cats.model

import cats.{Eq, Monoid, Show}
import cats.syntax.contravariant._
import org.typelevel.ci.CIString

case class TraceHeaders(values: Map[CIString, String]) extends AnyVal {
  def ++(that: TraceHeaders): TraceHeaders = TraceHeaders(this.values ++ that.values)
  def +(elem: (String, String)): TraceHeaders = {
    val (k, v) = elem
    TraceHeaders(values.updated(CIString(k), v))
  }
}

object TraceHeaders {
  def of(values: Map[String, String]): TraceHeaders = TraceHeaders(values.map { case (k, v) => CIString(k) -> v })
  def of(values: (String, String)*): TraceHeaders = of(values.toMap)
  def ofCi(values: (CIString, String)*): TraceHeaders = TraceHeaders(values.toMap)

  trait Converter[T] {
    def from(t: T): TraceHeaders
    def to(h: TraceHeaders): T
  }

  val empty: TraceHeaders = TraceHeaders(Map.empty)

  implicit val traceHeadersMonoid: Monoid[TraceHeaders] = Monoid.instance(empty, _ ++ _)
  implicit val traceHeadersShow: Show[TraceHeaders] = Show.catsShowForMap[CIString, String].contramap(_.values)
  implicit val traceHeadersEq: Eq[TraceHeaders] = Eq.catsKernelEqForMap[CIString, String].contramap(_.values)
}
