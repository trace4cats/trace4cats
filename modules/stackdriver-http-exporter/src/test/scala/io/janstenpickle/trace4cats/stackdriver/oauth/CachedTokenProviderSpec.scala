package io.janstenpickle.trace4cats.stackdriver.oauth

import cats.effect.IO
import cats.effect.testkit.TestInstances
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.ScalacheckShapeless._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.duration._
import scala.util.Success

class CachedTokenProviderSpec extends AnyFlatSpec with Matchers with ScalaCheckDrivenPropertyChecks with TestInstances {
  implicit val longArb: Arbitrary[Long] = Arbitrary(Gen.posNum[Long])

  it should "return a cached token when clock tick is less than expiry" in forAll {
    (token1: AccessToken, token2: AccessToken) =>
      implicit val ticker = Ticker()

      val updatedToken1 = token1.copy(expiresIn = 2)
      val provider = testTokenProvider(updatedToken1, token2)

      val test = for {
        cached <- CachedTokenProvider[IO](provider, 0.seconds)
        first <- cached.accessToken
        _ <- IO.sleep(1.second)
        second <- cached.accessToken
      } yield {
        first.copy(expiresIn = 1) should be(second)
        first should be(updatedToken1)
        if (token1.accessToken != token2.accessToken) first.accessToken should not be (token2.accessToken)
        ()
      }

      val result = test.unsafeToFuture()
      ticker.ctx.tickAll(10.seconds)
      result.value shouldEqual Some(Success(()))
  }

  it should "return a new token when clock tick is greater than expiry" in forAll {
    (token1: AccessToken, token2: AccessToken) =>
      implicit val ticker = Ticker()

      val updatedToken1 = token1.copy(expiresIn = 1)
      val provider = testTokenProvider(updatedToken1, token2)

      val test = for {
        cached <- CachedTokenProvider[IO](provider, 0.seconds)
        first <- cached.accessToken
        _ <- IO.sleep(2.seconds)
        second <- cached.accessToken
      } yield {
        first should be(updatedToken1)
        second should be(token2)
        ()
      }

      val result = test.unsafeToFuture()
      ticker.ctx.tickAll(10.seconds)
      result.value shouldEqual Some(Success(()))
  }

  def testTokenProvider(first: AccessToken, second: AccessToken): TokenProvider[IO] =
    new TokenProvider[IO] {
      var invCount = 0

      override val accessToken: IO[AccessToken] = IO {
        val token =
          if (invCount == 0) first
          else second

        invCount = invCount + 1
        token
      }
    }
}
