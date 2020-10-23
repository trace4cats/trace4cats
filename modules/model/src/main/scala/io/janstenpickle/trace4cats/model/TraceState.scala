package io.janstenpickle.trace4cats.model

import cats.{Eq, Show}
import cats.syntax.contravariant._
import io.janstenpickle.trace4cats.model.TraceState.{Key, Value}

case class TraceState private (values: Map[Key, Value]) extends AnyVal

object TraceState {
  def empty: TraceState = new TraceState(Map.empty)
  def apply(values: Map[Key, Value]): Option[TraceState] =
    if (values.size > 32) None else Some(new TraceState(values))

  case class Key private (k: String) extends AnyVal {
    override def toString: String = k
  }
  object Key {
    private val regex = "^([0-9a-z_\\-*/]+)$".r
    def apply(k: String): Option[Key] =
      if (regex.findFirstMatchIn(k).isDefined) Some(new Key(k)) else None
    def unsafe(k: String): Key = apply(k).getOrElse(throw new RuntimeException("Invalid trace state key format"))

    implicit val show: Show[Key] = Show.show(_.k)
    implicit val eq: Eq[Key] = Eq.by(_.k)
  }

  case class Value private (v: String) extends AnyVal {
    override def toString: String = v
  }
  object Value {
    private val regex = "((,|=|\\s)+)".r
    def apply(v: String): Option[Value] =
      if (v.length > 256 && regex.findFirstMatchIn(v).isDefined) None else Some(new Value(v))
    def unsafe(v: String): Value = apply(v).getOrElse(throw new RuntimeException("Invalid trace state value format"))

    implicit val show: Show[Value] = Show.show(_.v)
    implicit val eq: Eq[Value] = Eq.by(_.v)
  }

  implicit val show: Show[TraceState] = Show[Map[Key, Value]].contramap(_.values)
  implicit val eq: Eq[TraceState] = Eq.by(_.values)
}
