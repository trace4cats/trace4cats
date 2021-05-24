package io.janstenpickle.trace4cats.`export`

import cats.derived.semiauto
import cats.kernel.Eq
import io.janstenpickle.trace4cats.`export`.ExportRetryConfig.NextDelay

import scala.concurrent.duration._

case class ExportRetryConfig(
  delay: FiniteDuration = 500.millis,
  nextDelay: NextDelay = NextDelay.Constant(),
  maxAttempts: Int = 100
)

object ExportRetryConfig {
  sealed trait NextDelay
  object NextDelay {
    case class Constant(delay: FiniteDuration = 100.millis) extends NextDelay
    case object Exponential extends NextDelay

    implicit val eq: Eq[NextDelay] = semiauto.eq
  }

  implicit val eq: Eq[ExportRetryConfig] = semiauto.eq
}
