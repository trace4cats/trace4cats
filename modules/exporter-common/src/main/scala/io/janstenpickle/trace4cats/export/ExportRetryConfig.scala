package io.janstenpickle.trace4cats.`export`

import scala.concurrent.duration._

case class ExportRetryConfig(
  delay: FiniteDuration = 500.millis,
  nextDelay: FiniteDuration => FiniteDuration = _ + 100.millis,
  maxAttempts: Int = 100
)
