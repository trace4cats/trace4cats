package io.janstenpickle.trace4cats.`export`

import cats.kernel.Eq

import scala.concurrent.duration._

case class ExportRetryConfig(
  delay: FiniteDuration = 500.millis,
  nextDelay: FiniteDuration => FiniteDuration = _ + 100.millis,
  maxAttempts: Int = 100
)

object ExportRetryConfig {
  implicit val eq: Eq[ExportRetryConfig] = Eq.by { config =>
    (config.delay, config.nextDelay(config.delay), config.maxAttempts)
  }
}
