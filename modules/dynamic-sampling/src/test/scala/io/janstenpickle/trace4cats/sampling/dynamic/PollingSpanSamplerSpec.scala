package io.janstenpickle.trace4cats.sampling.dynamic

import cats.effect.kernel.Resource
import cats.effect.{IO, Ref}
import cats.effect.testkit.TestInstances
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanKind, TraceId}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import cats.syntax.all._
import scala.concurrent.duration._
import scala.util.Success

class PollingSpanSamplerSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with ArbitraryInstances
    with TestInstances {

  behavior.of("ConfigPollingSpanSampler.updateConfig")

  it should "swap between samplers" in {
    implicit val ticker = Ticker()

    val test =
      Ref.of[IO, (String, Resource[IO, SpanSampler[IO]])](("always", Resource.pure(SpanSampler.always))).flatMap {
        samplerRef =>
          PollingSpanSampler[IO, String](
            samplerRef.get.map(_._1),
            _ => Resource.eval(samplerRef.get.map(_._2)).flatten,
            1.second
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

    val result = test.unsafeToFuture()
    ticker.ctx.tick(4.seconds)
    result.value shouldEqual Some(Success((SampleDecision.Include, SampleDecision.Drop, SampleDecision.Include)))
  }
}
