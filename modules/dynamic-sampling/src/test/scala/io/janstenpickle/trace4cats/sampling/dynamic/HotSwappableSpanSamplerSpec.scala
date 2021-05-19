package io.janstenpickle.trace4cats.sampling.dynamic

import cats.effect.IO
import cats.effect.testkit.TestInstances
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanKind, TraceId}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.util.Success

class HotSwappableSpanSamplerSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with ArbitraryInstances
    with TestInstances {

  behavior.of("HotSwappableSpanSampler.updateConfig")

  it should "swap between samplers" in {
    implicit val ticker = Ticker()

    val test = HotSwappableSpanSampler.create[IO](SamplerConfig.Always).use { sampler =>
      val decision = sampler.shouldSample(None, TraceId.invalid, "test", SpanKind.Internal)

      for {
        d0 <- decision
        _ <- sampler.updateConfig(SamplerConfig.Never)
        d1 <- decision
        _ <- sampler.updateConfig(SamplerConfig.Always)
        d2 <- decision
      } yield (d0, d1, d2)
    }

    val result = test.unsafeToFuture()
    ticker.ctx.tick()
    result.value shouldEqual Some(Success((SampleDecision.Include, SampleDecision.Drop, SampleDecision.Include)))
  }
}
