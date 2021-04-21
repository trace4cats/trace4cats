/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.interop

import cats.effect.testkit.TestInstances
import cats.effect.{IO => CIO}
import cats.syntax.all._
import cats.{Eq, Order}
import org.scalacheck.{Arbitrary, Cogen, Gen, Prop}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.prop.Configuration
import org.typelevel.discipline.Laws
import org.typelevel.discipline.scalatest.FunSuiteDiscipline
import zio._
import zio.clock.Clock
import zio.duration._
import zio.internal.{Executor, Platform, Tracing}

import java.time.{DateTimeException, Instant, OffsetDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration.Infinite
import scala.concurrent.duration.{FiniteDuration, TimeUnit}

private[zio] trait CatsSpecBase
    extends AnyFunSuite
    with FunSuiteDiscipline
    with Configuration
    with TestInstances
    with CatsSpecBaseLowPriority
    with zio.interop.PlatformSpecific {

  def checkAllAsync(name: String, f: Ticker => Laws#RuleSet): Unit =
    checkAll(name, f(Ticker()))

  def platform(implicit ticker: Ticker): Platform =
    Platform
      .fromExecutor(Executor.fromExecutionContext(1024)(ticker.ctx))
      .withTracing(Tracing.disabled)
      .withReportFailure(_ => ())

  def environment(implicit ticker: Ticker): ZEnv = {

    val testBlocking = new CBlockingService {
      def blockingExecutor: Executor =
        Executor.fromExecutionContext(1024)(ticker.ctx)
    }

    val testClock = new Clock.Service {
      def currentTime(unit: TimeUnit): UIO[Long] =
        ZIO.effectTotal(ticker.ctx.now().toUnit(unit).toLong)

      val currentDateTime: IO[DateTimeException, OffsetDateTime] =
        ZIO.effectTotal(OffsetDateTime.ofInstant(Instant.ofEpochMilli(ticker.ctx.now().toMillis), ZoneOffset.UTC))

      val nanoTime: UIO[Long] =
        ZIO.effectTotal(ticker.ctx.now().toNanos)

      def sleep(duration: Duration): UIO[Unit] = duration.asScala match {
        case finite: FiniteDuration =>
          ZIO.effectAsyncInterrupt { cb =>
            val cancel = ticker.ctx.schedule(finite, () => cb(UIO.unit))
            Left(UIO.effectTotal(cancel()))
          }
        case infinite: Infinite =>
          ZIO.dieMessage(s"Unexpected infinite duration $infinite passed to Ticker")
      }
    }

    ZEnv.Services.live ++ Has(testClock) ++ Has(testBlocking)
  }

  def unsafeRun[A](uio: UIO[A])(implicit ticker: Ticker): Exit[Nothing, Option[A]] =
    try {
      var exit = Exit.succeed(Option.empty[A])
      runtime.unsafeRunAsync[Nothing, Option[A]](uio.asSome)(exit = _)
      ticker.ctx.tickAll(FiniteDuration(1, TimeUnit.SECONDS))
      exit
    } catch {
      case error: Throwable =>
        error.printStackTrace()
        throw error
    }

  implicit def runtime(implicit ticker: Ticker): Runtime[Any] =
    Runtime((), platform)

  implicit val arbitraryAny: Arbitrary[Any] =
    Arbitrary(Gen.const(()))

  implicit val cogenForAny: Cogen[Any] =
    Cogen(_.hashCode.toLong)

  implicit def arbitraryEnvironment(implicit ticker: Ticker): Arbitrary[ZEnv] =
    Arbitrary(Gen.const(environment))

  implicit val eqForNothing: Eq[Nothing] =
    Eq.allEqual

  implicit val eqForExecutionContext: Eq[ExecutionContext] =
    Eq.allEqual

  implicit val eqForCauseOfNothing: Eq[Cause[Nothing]] =
    (x, y) => (x.interrupted && y.interrupted) || x == y

  implicit def eqForExitOfNothing[A: Eq]: Eq[Exit[Nothing, A]] = {
    case (Exit.Success(x), Exit.Success(y)) => x eqv y
    case (Exit.Failure(x), Exit.Failure(y)) => x eqv y
    case _                                  => false
  }

  implicit def eqForUIO[A: Eq](implicit ticker: Ticker): Eq[UIO[A]] = { (uio1, uio2) =>
    val exit1 = unsafeRun(uio1)
    val exit2 = unsafeRun(uio2)
    (exit1 eqv exit2) || {
      println(s"$exit1 was not equal to $exit2")
      false
    }
  }

  implicit def eqForURIO[R: Arbitrary, A: Eq](implicit ticker: Ticker): Eq[URIO[R, A]] =
    eqForZIO[R, Nothing, A]

  implicit def execRIO(rio: RIO[ZEnv, Boolean])(implicit ticker: Ticker): Prop =
    rio.provide(environment).toEffect[CIO]

  implicit def orderForUIOofFiniteDuration(implicit ticker: Ticker): Order[UIO[FiniteDuration]] =
    Order.by(unsafeRun(_).toEither.toOption)

  implicit def orderForRIOofFiniteDuration[R: Arbitrary](implicit ticker: Ticker): Order[RIO[R, FiniteDuration]] =
    (x, y) => Arbitrary.arbitrary[R].sample.fold(0)(r => x.orDie.provide(r) compare y.orDie.provide(r))

  implicit def eqForUManaged[A: Eq](implicit ticker: Ticker): Eq[UManaged[A]] =
    zManagedEq[Any, Nothing, A]

  implicit def eqForURManaged[R: Arbitrary, A: Eq](implicit ticker: Ticker): Eq[URManaged[R, A]] =
    zManagedEq[R, Nothing, A]
}

private[interop] sealed trait CatsSpecBaseLowPriority { this: CatsSpecBase =>

  implicit def eqForIO[E: Eq, A: Eq](implicit ticker: Ticker): Eq[IO[E, A]] =
    Eq.by(_.either)

  implicit def eqForZIO[R: Arbitrary, E: Eq, A: Eq](implicit ticker: Ticker): Eq[ZIO[R, E, A]] =
    (x, y) => Arbitrary.arbitrary[R].sample.exists(r => x.provide(r) eqv y.provide(r))

  implicit def eqForRIO[R: Arbitrary, A: Eq](implicit ticker: Ticker): Eq[RIO[R, A]] =
    eqForZIO[R, Throwable, A]

  implicit def eqForTask[A: Eq](implicit ticker: Ticker): Eq[Task[A]] =
    eqForIO[Throwable, A]

  def zManagedEq[R, E, A](implicit zio: Eq[ZIO[R, E, A]]): Eq[ZManaged[R, E, A]] =
    Eq.by(managed => ZManaged.ReleaseMap.make.flatMap(rm => managed.zio.provideSome[R](_ -> rm).map(_._2)))

  implicit def eqForRManaged[R: Arbitrary, A: Eq](implicit ticker: Ticker): Eq[RManaged[R, A]] =
    zManagedEq[R, Throwable, A]

  implicit def eqForManaged[E: Eq, A: Eq](implicit ticker: Ticker): Eq[Managed[E, A]] =
    zManagedEq[Any, E, A]

  implicit def eqForZManaged[R: Arbitrary, E: Eq, A: Eq](implicit ticker: Ticker): Eq[ZManaged[R, E, A]] =
    zManagedEq[R, E, A]

  implicit def eqForTaskManaged[A: Eq](implicit ticker: Ticker): Eq[TaskManaged[A]] =
    zManagedEq[Any, Throwable, A]
}
