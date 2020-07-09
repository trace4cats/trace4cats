// Adapted from Natchez
// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package io.janstenpickle.trace4cats.model

import cats.{Eq, Show}
import cats.instances.string._
import cats.instances.double._
import cats.instances.long._

sealed trait AttributeValue extends Product with Serializable {
  def value: Any
  override def toString: String = value.toString
}

object AttributeValue {

  case class StringValue(value: String) extends AttributeValue
  case class BooleanValue(value: Boolean) extends AttributeValue
  case class DoubleValue(value: Double) extends AttributeValue
  case class LongValue(value: Long) extends AttributeValue

  implicit def stringToTraceValue(value: String): AttributeValue = StringValue(value)
  implicit def boolToTraceValue(value: Boolean): AttributeValue = BooleanValue(value)
  implicit def intToTraceValue(value: Int): AttributeValue = LongValue(value.toLong)
  implicit def doubleToTraceValue(value: Double): AttributeValue = DoubleValue(value)

  implicit val show: Show[AttributeValue] = Show(_.toString)
  implicit val eq: Eq[AttributeValue] = Eq.instance {
    case (StringValue(x), StringValue(y)) => Eq[String].eqv(x, y)
    case (BooleanValue(x), BooleanValue(y)) => x == y
    case (DoubleValue(x), DoubleValue(y)) => Eq[Double].eqv(x, y)
    case (LongValue(x), LongValue(y)) => Eq[Long].eqv(x, y)
    case (_, _) => false
  }
}
