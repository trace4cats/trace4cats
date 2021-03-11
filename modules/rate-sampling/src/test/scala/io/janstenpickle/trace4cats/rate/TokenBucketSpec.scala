//TODO: fix with ticker

//package io.janstenpickle.trace4cats.rate
//
//import cats.effect.IO
//import cats.effect.kernel.Outcome
//import cats.effect.testkit.TestInstances
////import cats.effect.unsafe.implicits.global
//import cats.syntax.applicative._
//import org.scalacheck.{Arbitrary, Gen}
//import org.scalatest.flatspec.AnyFlatSpec
//import org.scalatest.matchers.should.Matchers
//import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
//
//import scala.concurrent.duration._
//
//class TokenBucketSpec extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks with TestInstances {
//  implicit val intArb: Arbitrary[Int] = Arbitrary(Gen.posNum[Int].suchThat(_ > 0))
//
//  behavior.of("TokenBucket.request1")
//
////  it should "issue a token when no time has passed" in forAll { (maxSize: Int, frequency: Int) =>
////    implicit val ticker = Ticker()
////
////
////    val tokenBucket = TokenBucket[IO](maxSize, frequency.seconds).unsafeRunSync()
////
////    tokenBucket.request1.unsafeRunSync() should be(true)
////  }
////
////  it should "issue multiple tokens" in forAll { (maxSize: Int) =>
////    val tokenBucket = TokenBucket[IO](maxSize, 1.seconds).unsafeRunSync()
////
////    List.fill(maxSize / 2)(tokenBucket.request1.unsafeRunSync()).forall(identity) should be(true)
////  }
////
////  it should "exhaust token bucket" in forAll { (tokenCount: Int) =>
////    val tokenBucket = TokenBucket[IO](tokenCount, 1.seconds).unsafeRunSync()
////
////    val (tokens, noTokens) = List.fill(tokenCount * 2)(tokenBucket.request1.unsafeRunSync()).partition(identity)
////
////    tokens.size should be(tokenCount)
////    noTokens.size should be(tokenCount)
////  }
//
//  it should "not issue more tokens than the max size" in forAll { (maxSize: Int, extras: Int) =>
//    implicit val ticker = Ticker()
//
//    val io = for {
//      tokenBucket <- TokenBucket[IO](maxSize, 1.seconds)
////      _ <- IO.sleep((maxSize + extras).seconds)
//      reqs <- tokenBucket.request1.replicateA(maxSize + extras)
//      (tokens, noTokens) = reqs.partition(identity)
//    } yield (tokens.size, noTokens.size)
//
//    ticker.ctx.tickAll((maxSize + extras).hours)
//    val result = unsafeRun(io)
//    result should be(Outcome.succeeded(Some((maxSize, extras))))
//  }
//
////  it should "replenish token bucket" in forAll { (tokenCount: Int) =>
////    implicit val ticker = Ticker()
////
////    val tokenBucket = TokenBucket[IO](tokenCount, 1.seconds).unsafeRunSync()
////
////    val (tokens, noTokens) = List.fill(tokenCount * 2)(tokenBucket.request1.unsafeRunSync()).partition(identity)
////
////    tokens.size should be(tokenCount)
////    noTokens.size should be(tokenCount)
////
////    ec.tick(1.seconds)
////    tokenBucket.request1.unsafeRunSync() should be(true)
////  }
//
////  behavior.of("TokenBucket.request")
////
////  it should "issue multiple tokens when no time has passed" in forAll { (maxSize: Int, frequency: Int) =>
////    val tokenBucket = TokenBucket[IO](maxSize, frequency.seconds).unsafeRunSync()
////
////    tokenBucket.request(maxSize).unsafeRunSync() should be(maxSize)
////  }
////
////  it should "not issue more tokens than the max size" in forAll { (tokenCount: Int) =>
////    val tokenBucket = TokenBucket[IO](tokenCount, 1.seconds).unsafeRunSync()
////
////    tokenBucket.request(tokenCount * 2).unsafeRunSync() should be(tokenCount)
////  }
//
////  it should "replenish token bucket" in forAll { (tokenCount: Int) =>
////    implicit val ticker = Ticker()
////
////    val tokenBucket = TokenBucket[IO](tokenCount, 1.seconds).unsafeRunSync()
////
////    tokenBucket.request(tokenCount * 2).unsafeRunSync() should be(tokenCount)
////
////    ec.tick(tokenCount.seconds)
////    tokenBucket.request(tokenCount).unsafeRunSync() should be(tokenCount)
////  }
//}
