package io.janstenpickle.trace4cats.sampling.dynamic

import cats.effect.{IO, Resource}
import cats.effect.testkit.TestInstances
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanKind, TraceId}
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.util.Success

class HotSwapSpanSamplerSpec
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckDrivenPropertyChecks
    with ArbitraryInstances
    with TestInstances {

  behavior.of("HotSwappableSpanSampler.updateConfig")

  it should "swap between samplers" in {
    implicit val ticker = Ticker()

    val test =
      HotSwapSpanSampler.create[IO, String]("always", Resource.pure(SpanSampler.always[IO])).use { sampler =>
        val decision = sampler.shouldSample(None, TraceId.invalid, "test", SpanKind.Internal)

        for {
          d0 <- decision
          updated0 <- sampler.swap("never", Resource.pure(SpanSampler.never))
          d1 <- decision
          updated1 <- sampler.swap("always", Resource.pure(SpanSampler.always))
          d2 <- decision
        } yield (d0, d1, d2, updated0, updated1)
      }

    val result = test.unsafeToFuture()
    ticker.ctx.tick()
    result.value shouldEqual Some(
      Success((SampleDecision.Include, SampleDecision.Drop, SampleDecision.Include, true, true))
    )
  }

  it should "not swap between samplers if the ID is identical" in {
    implicit val ticker = Ticker()

    val test =
      HotSwapSpanSampler.create[IO, String]("id", Resource.pure(SpanSampler.always[IO])).use { sampler =>
        val decision = sampler.shouldSample(None, TraceId.invalid, "test", SpanKind.Internal)

        for {
          d0 <- decision
          updated0 <- sampler.swap("id", Resource.pure(SpanSampler.never))
          d1 <- decision
          updated1 <- sampler.swap("id", Resource.pure(SpanSampler.always))
          d2 <- decision
        } yield (d0, d1, d2, updated0, updated1)
      }

    val result = test.unsafeToFuture()
    ticker.ctx.tick()
    result.value shouldEqual Some(
      Success((SampleDecision.Include, SampleDecision.Include, SampleDecision.Include, false, false))
    )
  }
}
