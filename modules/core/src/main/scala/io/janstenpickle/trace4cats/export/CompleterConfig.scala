package io.janstenpickle.trace4cats.`export`

import cats.kernel.Eq

import scala.concurrent.duration._

case class CompleterConfig(
  bufferSize: Int = 2000,
  batchSize: Int = 50,
  batchTimeout: FiniteDuration = 10.seconds,
  retryConfig: ExportRetryConfig = ExportRetryConfig()
)

object CompleterConfig {
  implicit val eq: Eq[CompleterConfig] =
    Eq.by(cc => (cc.bufferSize, cc.batchSize, cc.batchTimeout, cc.retryConfig))
}
