package io.janstenpickle.trace4cats.rate

import java.util.concurrent.{ScheduledExecutorService, ScheduledThreadPoolExecutor}
import cats.effect.{ContextShift, IO, Timer}
import cats.effect.laws.util.TestContext
import cats.syntax.applicative._
import cats.syntax.flatMap._
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.duration._
import scala.util.Success

class TokenBucketSpec extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks {
  val ec: TestContext = TestContext()
  implicit val timer: Timer[IO] = ec.ioTimer
  implicit val ctx: ContextShift[IO] = ec.ioContextShift

  val sc: ScheduledExecutorService = new ScheduledThreadPoolExecutor(1)

  implicit val intArb: Arbitrary[Int] = Arbitrary(Gen.posNum[Int].suchThat(_ > 0))

  behavior.of("TokenBucket.request1")

  it should "issue a token when no time has passed" in forAll { (maxSize: Int, frequency: Int) =>
    val test = TokenBucket.create[IO](maxSize, frequency.seconds).use { bucket =>
      bucket.request1
    }

    val result = test.unsafeToFuture()
    ec.tick(10.seconds)
    result.value shouldEqual Some(Success(true))
  }

  it should "issue multiple tokens" in forAll { (maxSize: Int) =>
    val test = TokenBucket.create[IO](maxSize, 1.seconds).use { bucket =>
      bucket.request1.replicateA(maxSize / 2).map(_.forall(identity))
    }

    val result = test.unsafeToFuture()
    ec.tick((maxSize + 10).seconds)
    result.value shouldEqual Some(Success(true))
  }

  it should "exhaust token bucket" in forAll { (tokenCount: Int) =>
    val test = TokenBucket.create[IO](tokenCount, 1.seconds).use { bucket =>
      bucket.request1
        .replicateA(tokenCount * 2)
        .map(_.partition(identity))
        .map { case (tokens, noTokens) => (tokens.size, noTokens.size) }
    }

    val result = test.unsafeToFuture()
    ec.tick((tokenCount * 2 + 10).seconds)
    result.value shouldEqual Some(Success((tokenCount, tokenCount)))
  }

  it should "not issue more tokens than the max size" in forAll { (maxSize: Int, extras: Int) =>
    val test = TokenBucket.create[IO](maxSize, 1.seconds).use { bucket =>
      timer.sleep((maxSize + extras).seconds) >>
        bucket.request1
          .replicateA(maxSize + extras)
          .map(_.partition(identity))
          .map { case (tokens, noTokens) => (tokens.size, noTokens.size) }
    }

    val result = test.unsafeToFuture()
    ec.tick((maxSize + extras + 10).seconds)
    result.value shouldEqual Some(Success((maxSize, extras)))
  }

  it should "replenish token bucket" in forAll { (tokenCount: Int) =>
    val test = TokenBucket.create[IO](tokenCount, 1.seconds).use { bucket =>
      for {
        (size, noSize) <- bucket.request1
          .replicateA(tokenCount * 2)
          .map(_.partition(identity))
          .map { case (tokens, noTokens) => (tokens.size, noTokens.size) }
        _ <- timer.sleep(2.second)
        req <- bucket.request1
      } yield (size, noSize, req)
    }

    val result = test.unsafeToFuture()
    ec.tick(10.seconds)
    result.value shouldEqual Some(Success((tokenCount, tokenCount, true)))
  }

  behavior.of("TokenBucket.request")

  it should "issue multiple tokens when no time has passed" in forAll { (maxSize: Int, frequency: Int) =>
    val test = TokenBucket.create[IO](maxSize, frequency.seconds).use { bucket =>
      bucket.request(maxSize)
    }

    val result = test.unsafeToFuture()
    ec.tick()
    result.value shouldEqual Some(Success(maxSize))
  }

  it should "not issue more tokens than the max size" in forAll { (tokenCount: Int) =>
    val test = TokenBucket.create[IO](tokenCount, 1.seconds).use { bucket =>
      bucket.request(tokenCount * 2)
    }

    val result = test.unsafeToFuture()
    ec.tick()
    result.value shouldEqual Some(Success(tokenCount))
  }

  it should "replenish token bucket" in forAll { (tokenCount: Int) =>
    val test = TokenBucket.create[IO](tokenCount, 1.seconds).use { bucket =>
      for {
        r1 <- bucket.request(tokenCount * 2)
        _ <- timer.sleep((tokenCount + 1).seconds)
        r2 <- bucket.request(tokenCount)
      } yield (r1, r2)
    }

    val result = test.unsafeToFuture()
    ec.tick((tokenCount + 10).seconds)
    result.value shouldEqual Some(Success((tokenCount, tokenCount)))
  }
}
