package trace4cats.model

import cats.Eq

case class TraceFlags(sampled: SampleDecision)

object TraceFlags {
  implicit val eq: Eq[TraceFlags] = Eq.by(_.sampled)
}
