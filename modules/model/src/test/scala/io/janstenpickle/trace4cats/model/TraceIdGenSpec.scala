package io.janstenpickle.trace4cats.model

import cats.effect.IO
import cats.effect.std.Random
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TraceIdGenSpec extends AnyFlatSpec with Matchers {
  behavior.of("TraceId.Gen[IO]")

  it should "generate distinct TraceId instances when using ThreadLocalRandom" in {
    val instance = implicitly[TraceId.Gen[IO]]
    GenAssertions.assertAllDistinct(instance.gen).unsafeRunSync()
  }

  it should "generate distinct TraceId instances when using custom Random" in {
    Random
      .scalaUtilRandom[IO]
      .flatMap { implicit random =>
        val instance = implicitly[TraceId.Gen[IO]]
        GenAssertions.assertAllDistinct(instance.gen)
      }
      .unsafeRunSync()
  }
}
