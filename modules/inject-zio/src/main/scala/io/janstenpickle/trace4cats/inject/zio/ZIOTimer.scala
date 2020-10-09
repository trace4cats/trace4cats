package io.janstenpickle.trace4cats.inject.zio

import cats.effect.{Clock, Timer}
import zio.ZIO
import zio.interop.catz.implicits.ioTimer

import scala.concurrent.duration.{FiniteDuration, TimeUnit}

trait ZIOTimer {
  implicit def timer[R]: Timer[ZIO[R, Throwable, *]] = new Timer[ZIO[R, Throwable, *]] {
    override val clock: Clock[ZIO[R, Throwable, *]] = new Clock[ZIO[R, Throwable, *]] {
      override def realTime(unit: TimeUnit): ZIO[R, Throwable, Long] = ioTimer.clock.realTime(unit)
      override def monotonic(unit: TimeUnit): ZIO[R, Throwable, Long] = ioTimer.clock.monotonic(unit)
    }

    override def sleep(duration: FiniteDuration): ZIO[R, Throwable, Unit] = ioTimer.sleep(duration)
  }

}
