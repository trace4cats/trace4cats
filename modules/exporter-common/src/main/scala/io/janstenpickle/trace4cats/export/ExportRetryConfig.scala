package io.janstenpickle.trace4cats.`export`

import cats.kernel.Eq
import cats.derived.semiauto

import scala.concurrent.duration._

case class ExportRetryConfig(
  delay: FiniteDuration = 500.millis,
  nextDelay: FiniteDuration => FiniteDuration = _ + 100.millis,
  maxAttempts: Int = 100
)

object ExportRetryConfig {
  implicit val nexDelayEq: Eq[FiniteDuration => FiniteDuration] = Eq.fromUniversalEquals

  implicit val eq: Eq[ExportRetryConfig] = semiauto.eq
}
