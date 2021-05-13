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

import org.scalacheck.{Arbitrary, Cogen, Gen}
import zio._
import zio.clock.Clock

trait ZioSpecBase extends CatsSpecBase with ZioSpecBaseLowPriority with GenIOInteropCats {

  implicit def arbitraryUIO[A: Arbitrary]: Arbitrary[UIO[A]] =
    Arbitrary(genUIO[A])

  implicit def arbitraryURIO[R: Cogen, A: Arbitrary]: Arbitrary[URIO[R, A]] =
    Arbitrary(Arbitrary.arbitrary[R => UIO[A]].map(ZIO.environment[R].flatMap))

  implicit def arbitraryUManaged[A: Arbitrary]: Arbitrary[UManaged[A]] =
    zManagedArbitrary[Any, Nothing, A](arbitraryUIO[A])

  implicit def arbitraryURManaged[R: Cogen, A: Arbitrary]: Arbitrary[URManaged[R, A]] =
    zManagedArbitrary[R, Nothing, A]

  implicit def arbitraryClockAndBlocking(implicit ticker: Ticker): Arbitrary[Clock & CBlocking] =
    Arbitrary(Arbitrary.arbitrary[ZEnv])

  implicit val cogenForClockAndBlocking: Cogen[Clock & CBlocking] =
    Cogen(_.hashCode.toLong)
}

private[interop] trait ZioSpecBaseLowPriority { self: ZioSpecBase =>

  implicit def arbitraryClock(implicit ticker: Ticker): Arbitrary[Clock] =
    Arbitrary(Arbitrary.arbitrary[ZEnv])

  implicit val cogenForClock: Cogen[Clock] =
    Cogen(_.hashCode.toLong)

  implicit def arbitraryIO[E: CanFail: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[IO[E, A]] = {
    implicitly[CanFail[E]]
    Arbitrary(Gen.oneOf(genIO[E, A], genLikeTrans(genIO[E, A]), genIdentityTrans(genIO[E, A])))
  }

  implicit def arbitraryZIO[R: Cogen, E: CanFail: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ZIO[R, E, A]] =
    Arbitrary(Gen.function1[R, IO[E, A]](arbitraryIO[E, A].arbitrary).map(ZIO.environment[R].flatMap))

  implicit def arbitraryRIO[R: Cogen, A: Arbitrary: Cogen]: Arbitrary[RIO[R, A]] =
    arbitraryZIO[R, Throwable, A]

  implicit def arbitraryTask[A: Arbitrary: Cogen]: Arbitrary[Task[A]] =
    arbitraryIO[Throwable, A]

  def zManagedArbitrary[R, E, A](implicit zio: Arbitrary[ZIO[R, E, A]]): Arbitrary[ZManaged[R, E, A]] =
    Arbitrary(zio.arbitrary.map(ZManaged.fromEffect))

  implicit def arbitraryRManaged[R: Cogen, A: Arbitrary: Cogen]: Arbitrary[RManaged[R, A]] =
    zManagedArbitrary[R, Throwable, A]

  implicit def arbitraryManaged[E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[Managed[E, A]] =
    zManagedArbitrary[Any, E, A]

  implicit def arbitraryZManaged[R: Cogen, E: Arbitrary: Cogen, A: Arbitrary: Cogen]: Arbitrary[ZManaged[R, E, A]] =
    zManagedArbitrary[R, E, A]

  implicit def arbitraryTaskManaged[A: Arbitrary: Cogen]: Arbitrary[TaskManaged[A]] =
    zManagedArbitrary[Any, Throwable, A]
}
