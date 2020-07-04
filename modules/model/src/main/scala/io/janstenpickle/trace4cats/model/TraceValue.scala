package io.janstenpickle.trace4cats.model

import cats.{Eq, Show}
import cats.instances.string._
import cats.instances.double._
import cats.instances.long._

sealed trait TraceValue extends Product with Serializable {
  def value: Any
  override def toString: String = value.toString
}

object TraceValue {

  case class StringValue(value: String) extends TraceValue
  case class BooleanValue(value: Boolean) extends TraceValue
  case class DoubleValue(value: Double) extends TraceValue
  case class LongValue(value: Long) extends TraceValue

  implicit def stringToTraceValue(value: String): TraceValue = StringValue(value)
  implicit def boolToTraceValue(value: Boolean): TraceValue = BooleanValue(value)
  implicit def intToTraceValue(value: Int): TraceValue = LongValue(value.toLong)
  implicit def doubleToTraceValue(value: Double): TraceValue = DoubleValue(value)

  implicit val show: Show[TraceValue] = Show(_.toString)
  implicit val eq: Eq[TraceValue] = Eq.instance {
    case (StringValue(x), StringValue(y)) => Eq[String].eqv(x, y)
    case (BooleanValue(x), BooleanValue(y)) => x == y
    case (DoubleValue(x), DoubleValue(y)) => Eq[Double].eqv(x, y)
    case (LongValue(x), LongValue(y)) => Eq[Long].eqv(x, y)
    case (_, _) => false
  }
}
