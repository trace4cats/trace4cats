package trace4cats.model

import cats.effect.IO
import cats.effect.std.Random
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SpanIdGenSpec extends AnyFlatSpec with Matchers {
  behavior.of("SpanId.Gen[IO]")

  it should "generate distinct SpanId instances when using ThreadLocalRandom" in {
    val instance = SpanId.Gen[IO]
    GenAssertions.assertAllDistinct(instance.gen).unsafeRunSync()
  }

  it should "generate distinct SpanId instances when using custom Random" in {
    Random
      .scalaUtilRandom[IO]
      .flatMap { implicit random =>
        val instance = SpanId.Gen[IO]
        GenAssertions.assertAllDistinct(instance.gen)
      }
      .unsafeRunSync()
  }
}
