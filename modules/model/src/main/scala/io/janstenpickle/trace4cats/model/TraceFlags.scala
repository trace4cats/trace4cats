package io.janstenpickle.trace4cats.model

import cats.Eq
import cats.instances.boolean._

case class TraceFlags(sampled: Boolean)

object TraceFlags {
  implicit val eq: Eq[TraceFlags] = Eq.by(_.sampled)
}
