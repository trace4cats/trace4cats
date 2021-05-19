package io.janstenpickle.trace4cats.rate

import cats.effect.IO
import cats.effect.testkit.TestInstances
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.util.Success

class DynamicTokenBucketSpec extends AnyFlatSpec with Matchers with TestInstances {
  behavior.of("DynamicTokenBucket.updateConfig")

  it should "update token bucket size config" in {
    implicit val ticker = Ticker()

    val test = DynamicTokenBucket.create[IO](1, 2.seconds).use { bucket =>
      for {
        tokens0 <- bucket.request(3)
        _ <- bucket.updateConfig(2, 2.seconds)
        _ <- IO.sleep(5.seconds)
        tokens1 <- bucket.request(3)
      } yield (tokens0, tokens1)
    }

    val result = test.unsafeToFuture()
    ticker.ctx.tick(5.seconds)
    result.value shouldEqual Some(Success((1, 2)))
  }

  it should "update token rate config" in {
    implicit val ticker = Ticker()

    val test = DynamicTokenBucket.create[IO](2, 1.second).use { bucket =>
      for {
        _ <- bucket.request(2)
        _ <- IO.sleep(2200.millis)
        tokens0 <- bucket.request(2)
        _ <- bucket.updateConfig(1, 10.seconds)
        _ <- IO.sleep(5.seconds)
        tokens1 <- bucket.request(2)
        _ <- IO.sleep(11.seconds)
        tokens2 <- bucket.request(2)
      } yield (tokens0, tokens1, tokens2)
    }

    val result = test.unsafeToFuture()
    ticker.ctx.tick(20.seconds)
    result.value shouldEqual Some(Success((2, 0, 1)))
  }

  behavior.of("DynamicTokenBucket.unsafeCreate")

  it should "cancel the update fiber when requested" in {
    implicit val ticker = Ticker()

    val test =
      for {
        (bucket, cancel) <- DynamicTokenBucket.unsafeCreate[IO](1, 1.seconds)
        tokens0 <- bucket.request(1)
        _ <- cancel
        _ <- IO.sleep(2.seconds)
        tokens1 <- bucket.request(2)
      } yield (tokens0, tokens1)

    val result = test.unsafeToFuture()
    ticker.ctx.tick(5.seconds)
    result.value shouldEqual Some(Success((1, 0)))
  }

  it should "not restart a canceled fiber" in {
    implicit val ticker = Ticker()

    val test =
      for {
        (bucket, cancel) <- DynamicTokenBucket.unsafeCreate[IO](1, 1.seconds)
        tokens0 <- bucket.request(1)
        _ <- cancel
        _ <- bucket.updateConfig(1, 1.seconds)
        _ <- IO.sleep(2.seconds)
        tokens1 <- bucket.request(2)
      } yield (tokens0, tokens1)

    val result = test.unsafeToFuture()
    ticker.ctx.tick(5.seconds)
    result.value shouldEqual Some(Success((1, 0)))
  }
}
