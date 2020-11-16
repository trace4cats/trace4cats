// Adapted from Natchez
// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package io.janstenpickle.trace4cats.model

import cats.data.NonEmptyList
import cats.kernel.Semigroup
import cats.{Eq, Order, Show}
import cats.syntax.foldable._
import cats.syntax.show._

sealed trait AttributeValue extends Product with Serializable {
  def value: Any
  override def toString: String = value.toString
  override def equals(obj: Any): Boolean =
    obj match {
      case other: AttributeValue => Eq.eqv(other, this)
      case _ => false
    }
}

object AttributeValue {

  case class StringValue(value: String) extends AttributeValue
  case class BooleanValue(value: Boolean) extends AttributeValue
  case class DoubleValue(value: Double) extends AttributeValue
  case class LongValue(value: Long) extends AttributeValue

  sealed trait AttributeList extends AttributeValue {
    override def value: NonEmptyList[Any]
    override def toString: String = value.map(_.toString).mkString_("[", ",", "]")
  }

  object AttributeList {
    private def listString[A: Show](fa: NonEmptyList[A]): String = fa.mkString_("[", ",", "]")

    implicit val show: Show[AttributeList] = Show.show {
      case StringList(value) => listString(value)(Show.show(s => s""""$s""""))
      case BooleanList(value) => listString(value)
      case DoubleList(value) => listString(value)
      case LongList(value) => listString(value)
    }
  }

  case class StringList(value: NonEmptyList[String]) extends AttributeList
  case class BooleanList(value: NonEmptyList[Boolean]) extends AttributeList
  case class DoubleList(value: NonEmptyList[Double]) extends AttributeList
  case class LongList(value: NonEmptyList[Long]) extends AttributeList

  implicit def stringToTraceValue(value: String): AttributeValue = StringValue(value)
  implicit def boolToTraceValue(value: Boolean): AttributeValue = BooleanValue(value)
  implicit def intToTraceValue(value: Int): AttributeValue = LongValue(value.toLong)
  implicit def doubleToTraceValue(value: Double): AttributeValue = DoubleValue(value)

  implicit val show: Show[AttributeValue] = Show.show {
    case StringValue(value) => value
    case BooleanValue(value) => value.show
    case DoubleValue(value) => value.show
    case LongValue(value) => value.show
    case list: AttributeList => list.show
  }

  implicit val eq: Eq[AttributeValue] = Eq.instance {
    case (StringValue(x), StringValue(y)) => Eq[String].eqv(x, y)
    case (BooleanValue(x), BooleanValue(y)) => x == y
    case (DoubleValue(x), DoubleValue(y)) => Eq[Double].eqv(x, y)
    case (LongValue(x), LongValue(y)) => Eq[Long].eqv(x, y)
    case (StringList(x), StringList(y)) => Eq[NonEmptyList[String]].eqv(x, y)
    case (BooleanList(x), BooleanList(y)) => Eq[NonEmptyList[Boolean]].eqv(x, y)
    case (DoubleList(x), DoubleList(y)) => Eq[NonEmptyList[Double]].eqv(x, y)
    case (LongList(x), LongList(y)) => Eq[NonEmptyList[Long]].eqv(x, y)
    case (_, _) => false
  }

  implicit val order: Order[AttributeValue] = Order.from {
    case (StringValue(x), StringValue(y)) => Order.compare(x, y)
    case (BooleanValue(x), BooleanValue(y)) => Order.compare(x, y)
    case (DoubleValue(x), DoubleValue(y)) => Order.compare(x, y)
    case (LongValue(x), LongValue(y)) => Order.compare(x, y)
    case (StringList(x), StringList(y)) => Order.compare(x, y)
    case (BooleanList(x), BooleanList(y)) => Order.compare(x, y)
    case (DoubleList(x), DoubleList(y)) => Order.compare(x, y)
    case (LongList(x), LongList(y)) => Order.compare(x, y)
    case (x, y) => Order.compare(x.show, y.show)
  }

  implicit val semigroup: Semigroup[AttributeValue] = Semigroup.instance {
    case (StringValue(x), StringValue(y)) => StringList(NonEmptyList.of(x, y).sorted)
    case (BooleanValue(x), BooleanValue(y)) => BooleanList(NonEmptyList.of(x, y).sorted)
    case (DoubleValue(x), DoubleValue(y)) => DoubleList(NonEmptyList.of(x, y).sorted)
    case (LongValue(x), LongValue(y)) => LongList(NonEmptyList.of(x, y).sorted)
    case (StringList(x), StringList(y)) => StringList((x ++ y.toList).sorted)
    case (BooleanList(x), BooleanList(y)) => BooleanList((x ++ y.toList).sorted)
    case (DoubleList(x), DoubleList(y)) => DoubleList((x ++ y.toList).sorted)
    case (LongList(x), LongList(y)) => LongList((x ++ y.toList).sorted)
    case (x: AttributeList, y: AttributeList) => StringList((x.value ++ y.value.toList).map(_.toString).sorted)
    case (x: AttributeList, y: AttributeValue) =>
      StringList(NonEmptyList(y.show, x.value.map(_.toString).toList).sorted)
    case (x: AttributeValue, y: AttributeList) =>
      StringList(NonEmptyList(x.show, y.value.map(_.toString).toList).sorted)
    case (x, y) => StringList(NonEmptyList.of(x.show, y.show).sorted)
  }

  implicit val ordering: Ordering[AttributeValue] = order.toOrdering
}
