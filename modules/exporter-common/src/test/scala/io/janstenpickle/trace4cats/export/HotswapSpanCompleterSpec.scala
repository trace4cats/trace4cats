package io.janstenpickle.trace4cats.`export`

import cats.effect.IO
import cats.effect.kernel.Outcome.Succeeded
import cats.effect.kernel.Resource
import cats.effect.testkit.TestInstances
import cats.syntax.all._
import io.janstenpickle.trace4cats.model._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.concurrent.duration._

class HotswapSpanCompleterSpec extends AnyFlatSpec with Matchers with TestInstances {

  behavior.of("HotSwapSpanSampler.update")

  def span0(time: Long) = CompletedSpan.Builder(
    SpanContext.invalid,
    "test1",
    SpanKind.Internal,
    Instant.now(),
    Instant.ofEpochMilli(time),
    Map.empty,
    SpanStatus.Ok,
    None,
    None
  )
  def span1(time: Long) = CompletedSpan.Builder(
    SpanContext.invalid,
    "test2",
    SpanKind.Internal,
    Instant.now(),
    Instant.ofEpochMilli(time),
    Map.empty,
    SpanStatus.Ok,
    None,
    None
  )
  val proc = TraceProcess("test")

  it should "swap span completer without having to wait for hotswap to complete" in {
    implicit val ticker = Ticker()

    val test = (for {
      refCompleter0 <- Resource.eval[IO, RefSpanCompleter[IO]](RefSpanCompleter[IO](proc))
      refCompleter1 <- Resource.eval[IO, RefSpanCompleter[IO]](RefSpanCompleter[IO](proc))
      hotswap <- HotswapSpanCompleter(
        "a",
        Resource.make[IO, RefSpanCompleter[IO]](IO(refCompleter0))(_ => IO.defer(IO.sleep(10.seconds)))
      )
      _ <- Resource.eval(hotswap.complete(span0((ticker.ctx.state.clock + 1.second).toMillis)))
      bg <- hotswap.update("b", Resource.pure(refCompleter1)).background // swap out the completer in the background
      _ <- Resource.eval(IO.sleep(1.second)) // give it a chance to update the internal ref
      _ <- Resource.eval(hotswap.complete(span1(ticker.ctx.state.clock.toMillis)))
    } yield (refCompleter0.get, refCompleter1.get, bg.flatMap(_.asInstanceOf[Succeeded[IO, Throwable, Boolean]].fa)))
      .use(_.tupled)

    val result = test.unsafeToFuture()
    ticker.ctx.tick(15.seconds)
    val res = result.value.get.get

    // each completer has been used
    res._1.size should be(1)
    res._2.size should be(1)
    // indicates completer has been swapped
    res._3 should be(true)
    // completion isn't blocked by hotswap
    res._1.head.end should be(res._2.head.end)
  }

  it should "not swap a completer when the IDs the same" in {
    implicit val ticker = Ticker()

    val test = (for {
      refCompleter0 <- Resource.eval[IO, RefSpanCompleter[IO]](RefSpanCompleter[IO](proc))
      refCompleter1 <- Resource.eval[IO, RefSpanCompleter[IO]](RefSpanCompleter[IO](proc))
      hotswap <- HotswapSpanCompleter(
        "a",
        Resource.make[IO, RefSpanCompleter[IO]](IO(refCompleter0))(_ => IO.defer(IO.sleep(10.seconds)))
      )
      _ <- Resource.eval(hotswap.complete(span0((ticker.ctx.state.clock + 1.second).toMillis)))
      bg <- hotswap.update("a", Resource.pure(refCompleter1)).background // swap out the completer in the background
      _ <- Resource.eval(IO.sleep(1.second)) // give it a chance to update the internal ref
      _ <- Resource.eval(hotswap.complete(span1(ticker.ctx.state.clock.toMillis)))
    } yield (refCompleter0.get, refCompleter1.get, bg.flatMap(_.asInstanceOf[Succeeded[IO, Throwable, Boolean]].fa)))
      .use(_.tupled)

    val result = test.unsafeToFuture()
    ticker.ctx.tick(15.seconds)
    val res = result.value.get.get

    // only one completer has been used
    res._1.size should be(2)
    res._2.size should be(0)
    // indicates completer has been swapped
    res._3 should be(false)
    // completed spans are all in one queue
    res._1.head.end should be(res._1(1).end)
  }
}
