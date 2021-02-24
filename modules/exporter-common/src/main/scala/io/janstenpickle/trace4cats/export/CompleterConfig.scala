package io.janstenpickle.trace4cats.`export`

import scala.concurrent.duration._

case class CompleterConfig(
  bufferSize: Int = 2000,
  batchSize: Int = 50,
  batchTimeout: FiniteDuration = 10.seconds,
  retryConfig: ExportRetryConfig = ExportRetryConfig()
)
