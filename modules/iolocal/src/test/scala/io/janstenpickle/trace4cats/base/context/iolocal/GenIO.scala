// Adapted from ZIO

/*
 * Copyright 2017-2021 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache LicensVersion 2.0 (the "License");
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

package io.janstenpickle.trace4cats.base.context.iolocal

import cats.effect.{Deferred, IO}
import org.scalacheck._

trait GenIO {

  def genSyncSuccess[A: Arbitrary]: Gen[IO[A]] = Arbitrary.arbitrary[A].map(IO.pure[A])

  def genAsyncSuccess[A: Arbitrary]: Gen[IO[A]] =
    Arbitrary.arbitrary[A].map(a => IO.async_[A](k => k(Right(a))))

  def genSuccess[A: Arbitrary]: Gen[IO[A]] = Gen.oneOf(genSyncSuccess[A], genAsyncSuccess[A])

  def genSyncFailure[A]: Gen[IO[A]] = Arbitrary.arbitrary[Throwable].map(IO.raiseError)

  def genAsyncFailure[A]: Gen[IO[A]] =
    Arbitrary.arbitrary[Throwable].map(err => IO.async_[A](k => k(Left(err))))

  def genFailure[A]: Gen[IO[A]] = Gen.oneOf(genSyncFailure[A], genAsyncFailure[A])

  def genIO[A: Arbitrary]: Gen[IO[A]] =
    Gen.oneOf(genSuccess[A], genFailure[A])

  def genLikeTrans[A: Arbitrary: Cogen](gen: Gen[IO[A]]): Gen[IO[A]] = {
    val functions: IO[A] => Gen[IO[A]] = io =>
      Gen.oneOf(
        genOfFlatMaps[A](io)(genSuccess[A]),
        genOfMaps[A](io),
        genOfRace[A](io),
        genOfParallel[A](io)(genSuccess[A])
      )
    gen.flatMap(io => genTransformations(functions)(io))
  }

  def genIdentityTrans[A: Arbitrary](gen: Gen[IO[A]]): Gen[IO[A]] = {
    val functions: IO[A] => Gen[IO[A]] = io =>
      Gen.oneOf(
        genOfIdentityFlatMaps[A](io),
        genOfIdentityMaps[A](io),
        genOfRace[A](io),
        genOfParallel[A](io)(genAsyncSuccess[A])
      )
    gen.flatMap(io => genTransformations(functions)(io))
  }

  private def genTransformations[A](functionGen: IO[A] => Gen[IO[A]])(io: IO[A]): Gen[IO[A]] =
    Gen.sized { size =>
      def append1(n: Int, io: IO[A]): Gen[IO[A]] =
        if (n <= 0) io
        else
          (for {
            updatedIO <- functionGen(io)
          } yield updatedIO).flatMap(append1(n - 1, _))
      append1(size, io)
    }

  private def genOfMaps[A: Arbitrary: Cogen](io: IO[A]): Gen[IO[A]] =
    Arbitrary.arbitrary[A => A].map(f => io.map(f))

  private def genOfIdentityMaps[A](io: IO[A]): Gen[IO[A]] = Gen.const(io.map(identity))

  private def genOfFlatMaps[A](io: IO[A])(gen: Gen[IO[A]]): Gen[IO[A]] =
    gen.map(nextIO => io.flatMap(_ => nextIO))

  private def genOfIdentityFlatMaps[A](io: IO[A]): Gen[IO[A]] =
    Gen.const(io.flatMap(a => IO.pure(a)))

  private def genOfRace[A](io: IO[A]): Gen[IO[A]] =
    Gen.const(io.race(IO.never[A])).map(_.map(_.merge))

  private def genOfParallel[A](io: IO[A])(gen: Gen[IO[A]]): Gen[IO[A]] =
    gen.map { parIo =>
      Deferred[IO, Unit].flatMap { p =>
        (parIo *> p.complete(())).background.surround(p.get *> io)
      }
    }
}
