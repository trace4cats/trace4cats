package trace4cats

import cats.effect.kernel.Resource
import cats.effect.testkit.{TestControl, TestInstances}
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}
import cats.syntax.all._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import trace4cats.dynamic.PollingSpanSampler
import trace4cats.model.{SampleDecision, SpanKind, TraceId}
import trace4cats.test.ArbitraryInstances

import scala.concurrent.duration._

class PollingSpanSamplerSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with ArbitraryInstances
    with TestInstances {

  behavior.of("ConfigPollingSpanSampler.updateConfig")

  it should "swap between samplers" in {
    val test =
      Ref.of[IO, (String, Resource[IO, SpanSampler[IO]])](("always", Resource.pure(SpanSampler.always))).flatMap {
        samplerRef =>
          PollingSpanSampler[IO, String](samplerRef.get.map(_._1), 1.second)(_ =>
            Resource.eval(samplerRef.get.map(_._2)).flatten
          ).use { sampler =>
            val decision = sampler.shouldSample(None, TraceId.invalid, "test", SpanKind.Internal)

            for {
              d0 <- decision
              _ <- samplerRef.set(("never", Resource.pure(SpanSampler.never)))
              _ <- IO.sleep(2.seconds)
              d1 <- decision
              _ <- samplerRef.set(("always", Resource.pure(SpanSampler.always)))
              _ <- IO.sleep(2.seconds)
              d2 <- decision
            } yield (d0, d1, d2)
          }
      }

    val result = TestControl.executeEmbed(test).unsafeRunSync()
    result shouldEqual ((SampleDecision.Include, SampleDecision.Drop, SampleDecision.Include))
  }
}
