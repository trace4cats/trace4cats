package io.janstenpickle.trace4cats.rate

import cats.effect.IO
import cats.effect.testkit.{TestControl, TestInstances}
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class DynamicTokenBucketSpec extends AnyFlatSpec with Matchers with TestInstances {
  behavior.of("DynamicTokenBucket.updateConfig")

  it should "update token bucket size config" in {
    val test = DynamicTokenBucket.create[IO](1, 2.seconds).use { bucket =>
      for {
        tokens0 <- bucket.request(3)
        _ <- bucket.updateConfig(2, 2.seconds)
        _ <- IO.sleep(5.seconds)
        tokens1 <- bucket.request(3)
      } yield (tokens0, tokens1)
    }

    val result = TestControl.executeEmbed(test).unsafeRunSync()
    result shouldEqual ((1, 2))
  }

  it should "update token rate config" in {
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

    val result = TestControl.executeEmbed(test).unsafeRunSync()
    result shouldEqual ((2, 0, 1))
  }
}
