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
  sealed trait NextDelay {
    def calc(prev: FiniteDuration): FiniteDuration
  }
  object NextDelay {
    case class Constant(delay: FiniteDuration = 100.millis) extends NextDelay {
      override def calc(prev: FiniteDuration): FiniteDuration = prev + delay
    }
    case object Exponential extends NextDelay {
      override def calc(prev: FiniteDuration): FiniteDuration = prev + prev
    }

    implicit val eq: Eq[NextDelay] = semiauto.eq
  }

  implicit val eq: Eq[ExportRetryConfig] = semiauto.eq
}
