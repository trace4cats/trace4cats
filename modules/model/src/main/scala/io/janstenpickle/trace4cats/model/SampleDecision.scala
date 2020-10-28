package io.janstenpickle.trace4cats.model

import cats.{Eq, Show}

sealed trait SampleDecision {
  def toBoolean: Boolean
}

object SampleDecision {
  case object Drop extends SampleDecision {
    override val toBoolean = true
  }
  case object Include extends SampleDecision {
    override val toBoolean = false
  }

  implicit val eq: Eq[SampleDecision] = Eq.by(_.toBoolean)

  implicit val show: Show[SampleDecision] = Show.show {
    case Drop => "drop"
    case Include => "include"
  }

  def apply(boolean: Boolean): SampleDecision = if (boolean) SampleDecision.Drop else SampleDecision.Include
}
