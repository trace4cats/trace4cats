package io.janstenpickle.trace4cats.rate

import java.util.concurrent.{ScheduledExecutorService, ScheduledThreadPoolExecutor}

import cats.effect.{ContextShift, IO, Timer}
import cats.effect.laws.util.TestContext
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.duration._

class TokenBucketSpec extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks {
  val ec: TestContext = TestContext()
  implicit val timer: Timer[IO] = ec.ioTimer
  implicit val ctx: ContextShift[IO] = ec.ioContextShift

  val sc: ScheduledExecutorService = new ScheduledThreadPoolExecutor(1)

  implicit val intArb: Arbitrary[Int] = Arbitrary(Gen.posNum[Int].suchThat(_ > 0))

  behavior.of("TokenBucket.request1")

  it should "issue a token when no time has passed" in forAll { (maxSize: Int, frequency: Int) =>
    val tokenBucket = TokenBucket[IO](maxSize, frequency.seconds).unsafeRunSync()

    tokenBucket.request1.unsafeRunSync() should be(true)
  }

  it should "issue multiple tokens" in forAll { (maxSize: Int) =>
    val tokenBucket = TokenBucket[IO](maxSize, 1.seconds).unsafeRunSync()

    List.fill(maxSize / 2)(tokenBucket.request1.unsafeRunSync()).forall(identity) should be(true)
  }

  it should "exhaust token bucket" in forAll { (tokenCount: Int) =>
    val tokenBucket = TokenBucket[IO](tokenCount, 1.seconds).unsafeRunSync()

    val (tokens, noTokens) = List.fill(tokenCount * 2)(tokenBucket.request1.unsafeRunSync()).partition(identity)

    tokens.size should be(tokenCount)
    noTokens.size should be(tokenCount)
  }

  it should "not issue more tokens than the max size" in forAll { (maxSize: Int, extras: Int) =>
    val tokenBucket = TokenBucket[IO](maxSize, 1.seconds).unsafeRunSync()

    ec.tick((maxSize + extras).seconds)

    val (tokens, noTokens) = List.fill(maxSize + extras)(tokenBucket.request1.unsafeRunSync()).partition(identity)

    tokens.size should be(maxSize)
    noTokens.size should be(extras)
  }

  it should "replenish token bucket" in forAll { (tokenCount: Int) =>
    val tokenBucket = TokenBucket[IO](tokenCount, 1.seconds).unsafeRunSync()

    val (tokens, noTokens) = List.fill(tokenCount * 2)(tokenBucket.request1.unsafeRunSync()).partition(identity)

    tokens.size should be(tokenCount)
    noTokens.size should be(tokenCount)

    ec.tick(1.seconds)
    tokenBucket.request1.unsafeRunSync() should be(true)
  }

  behavior.of("TokenBucket.request")

  it should "issue multiple tokens when no time has passed" in forAll { (maxSize: Int, frequency: Int) =>
    val tokenBucket = TokenBucket[IO](maxSize, frequency.seconds).unsafeRunSync()

    tokenBucket.request(maxSize).unsafeRunSync() should be(maxSize)
  }

  it should "not issue more tokens than the max size" in forAll { (tokenCount: Int) =>
    val tokenBucket = TokenBucket[IO](tokenCount, 1.seconds).unsafeRunSync()

    tokenBucket.request(tokenCount * 2).unsafeRunSync() should be(tokenCount)
  }

  it should "replenish token bucket" in forAll { (tokenCount: Int) =>
    val tokenBucket = TokenBucket[IO](tokenCount, 1.seconds).unsafeRunSync()

    tokenBucket.request(tokenCount * 2).unsafeRunSync() should be(tokenCount)

    ec.tick(tokenCount.seconds)
    tokenBucket.request(tokenCount).unsafeRunSync() should be(tokenCount)
  }
}
