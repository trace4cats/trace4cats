package io.janstenpickle.trace4cats.rate

import scala.concurrent.duration._

object TokenInterval {
  def apply(rate: Double): Option[FiniteDuration] = Some(1.second / rate).collect { case dur: FiniteDuration => dur }
}
