// Adapted from Natchez
// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package io.janstenpickle.trace4cats.model

import cats.data.NonEmptyList
import cats.kernel.Semigroup
import cats.{Eq, Eval, Order, Show}
import cats.syntax.foldable._
import cats.syntax.show._

sealed trait AttributeValue extends Any {
  def value: Eval[Any]
  override def toString: String = value.value.toString
  override def equals(obj: Any): Boolean =
    obj match {
      case other: AttributeValue => Eq.eqv(other, this)
      case _ => false
    }
}

object AttributeValue {

  case class StringValue(value: Eval[String]) extends AnyVal with AttributeValue
  object StringValue {
    def apply(value: => String): StringValue = new StringValue(Eval.later(value))
  }
  case class BooleanValue(value: Eval[Boolean]) extends AnyVal with AttributeValue
  object BooleanValue {
    def apply(value: => Boolean): BooleanValue = new BooleanValue(Eval.later(value))
  }
  case class DoubleValue(value: Eval[Double]) extends AnyVal with AttributeValue
  object DoubleValue {
    def apply(value: => Double): DoubleValue = new DoubleValue(Eval.later(value))
  }
  case class LongValue(value: Eval[Long]) extends AnyVal with AttributeValue
  object LongValue {
    def apply(value: => Long): LongValue = new LongValue(Eval.later(value))
  }

  sealed trait AttributeList extends AttributeValue {
    override def value: Eval[NonEmptyList[Any]]
    override def toString: String = value.value.map(_.toString).mkString_("[", ",", "]")
  }

  object AttributeList {
    private def listString[A: Show](fa: NonEmptyList[A]): String = fa.mkString_("[", ",", "]")

    implicit val show: Show[AttributeList] = Show.show {
      case StringList(value) => listString(value.value)(Show.show(s => s""""$s""""))
      case BooleanList(value) => listString(value.value)
      case DoubleList(value) => listString(value.value)
      case LongList(value) => listString(value.value)
    }
  }

  case class StringList(value: Eval[NonEmptyList[String]]) extends AttributeList
  object StringList {
    def apply(value: => NonEmptyList[String]): StringList = new StringList(Eval.later(value))
  }
  case class BooleanList(value: Eval[NonEmptyList[Boolean]]) extends AttributeList
  object BooleanList {
    def apply(value: => NonEmptyList[Boolean]): BooleanList = new BooleanList(Eval.later(value))
  }
  case class DoubleList(value: Eval[NonEmptyList[Double]]) extends AttributeList
  object DoubleList {
    def apply(value: => NonEmptyList[Double]): DoubleList = new DoubleList(Eval.later(value))
  }
  case class LongList(value: Eval[NonEmptyList[Long]]) extends AttributeList
  object LongList {
    def apply(value: => NonEmptyList[Long]): LongList = new LongList(Eval.later(value))
  }

  implicit def stringToTraceValue(value: => String): AttributeValue = StringValue(value)
  implicit def boolToTraceValue(value: => Boolean): AttributeValue = BooleanValue(value)
  implicit def intToTraceValue(value: => Int): AttributeValue = LongValue(value.toLong)
  implicit def doubleToTraceValue(value: => Double): AttributeValue = DoubleValue(value)

  implicit val show: Show[AttributeValue] = Show.show {
    case StringValue(value) => value.value
    case BooleanValue(value) => value.value.show
    case DoubleValue(value) => value.value.show
    case LongValue(value) => value.value.show
    case list: AttributeList => list.show
  }

  implicit val order: Order[AttributeValue] = Order.from {
    case (StringValue(x), StringValue(y)) => Order.compare(x, y)
    case (BooleanValue(x), BooleanValue(y)) => Order.compare(x, y)
    case (DoubleValue(x), DoubleValue(y)) => Order.compare(x, y)
    case (DoubleValue(x), LongValue(y)) => Order.compare(x.value, y.value.toDouble)
    case (LongValue(x), LongValue(y)) => Order.compare(x, y)
    case (LongValue(x), DoubleValue(y)) => Order.compare(x.value.toDouble, y.value)
    case (StringList(x), StringList(y)) => Order.compare(x, y)
    case (BooleanList(x), BooleanList(y)) => Order.compare(x, y)
    case (DoubleList(x), DoubleList(y)) => Order.compare(x, y)
    case (DoubleList(x), LongList(y)) => Order.compare(x.value, y.value.map(_.toDouble))
    case (LongList(x), LongList(y)) => Order.compare(x, y)
    case (LongList(x), DoubleList(y)) => Order.compare(x.value.map(_.toDouble), y.value)
    case (x, y) => Order.compare(x.show, y.show)
  }

  implicit val semigroup: Semigroup[AttributeValue] = Semigroup.instance {
    case (StringValue(x), StringValue(y)) => StringList(Eval.later(NonEmptyList.of(x.value, y.value).sorted))
    case (StringValue(x), StringList(y)) => StringList(Eval.later(NonEmptyList(x.value, y.value.toList).sorted))
    case (StringList(x), StringValue(y)) => StringList(Eval.later(NonEmptyList(y.value, x.value.toList).sorted))
    case (StringList(x), StringList(y)) => StringList(Eval.later((x.value ++ y.value.toList).sorted))

    case (BooleanValue(x), BooleanValue(y)) => BooleanList(Eval.later(NonEmptyList.of(x.value, y.value).sorted))
    case (BooleanValue(x), BooleanList(y)) => BooleanList(Eval.later(NonEmptyList(x.value, y.value.toList).sorted))
    case (BooleanList(x), BooleanValue(y)) => BooleanList(Eval.later(NonEmptyList(y.value, x.value.toList).sorted))
    case (BooleanList(x), BooleanList(y)) => BooleanList(Eval.later((x.value ++ y.value.toList).sorted))

    case (DoubleValue(x), DoubleValue(y)) => DoubleList(Eval.later(NonEmptyList.of(x.value, y.value).sorted))
    case (DoubleValue(x), DoubleList(y)) => DoubleList(Eval.later(NonEmptyList(x.value, y.value.toList).sorted))
    case (DoubleList(x), DoubleValue(y)) => DoubleList(Eval.later(NonEmptyList(y.value, x.value.toList).sorted))
    case (DoubleList(x), DoubleList(y)) => DoubleList(Eval.later((x.value ++ y.value.toList).sorted))

    case (LongValue(x), LongValue(y)) => LongList(Eval.later(NonEmptyList.of(x.value, y.value).sorted))
    case (LongValue(x), LongList(y)) => LongList(Eval.later(NonEmptyList(x.value, y.value.toList).sorted))
    case (LongList(x), LongValue(y)) => LongList(Eval.later(NonEmptyList(y.value, x.value.toList).sorted))
    case (LongList(x), LongList(y)) => LongList(Eval.later((x.value ++ y.value.toList).sorted))

    case (x: AttributeList, y: AttributeList) =>
      StringList(Eval.later((x.value.value ++ y.value.value.toList).map(_.toString).sorted))
    case (x: AttributeList, y: AttributeValue) =>
      StringList(Eval.later(NonEmptyList(y.show, x.value.value.map(_.toString).toList).sorted))
    case (x: AttributeValue, y: AttributeList) =>
      StringList(Eval.later(NonEmptyList(x.show, y.value.value.map(_.toString).toList).sorted))
    case (x, y) => StringList(Eval.later(NonEmptyList.of(x.show, y.show).sorted))
  }

  implicit val ordering: Ordering[AttributeValue] = order.toOrdering
}
