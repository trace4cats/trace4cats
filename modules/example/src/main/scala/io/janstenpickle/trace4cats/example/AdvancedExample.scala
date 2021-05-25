package io.janstenpickle.trace4cats.example

import cats.effect.{ExitCode, IO, IOApp}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.model.{SpanKind, SpanStatus, TraceProcess}
import io.janstenpickle.trace4cats.rate.sampling.RateSpanSampler
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration._

/** This example is similar the to the simple exmample, however multiple completers are used in parallel and
  * span resources are flatmapped rather than the `use` method being called - this has essentially the effect
  * as the simple example call tree.
  */
object AdvancedExample extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = Slf4jLogger.create[IO].flatMap { implicit logger: Logger[IO] =>
    (for {
      completer <- AllCompleters[IO](TraceProcess("test"))

      // Set up rate sampler
      rateSampler <- RateSpanSampler.create[IO](bucketSize = 100, tokenInterval = 100.millis)

      // as shown in the simple example, Spans are `cats.effect.Resource`s so may be flatMapped with others
      root <- Span.root[IO]("root", SpanKind.Client, rateSampler, completer)
      child <- root.child("child", SpanKind.Server)
    } yield child).use(_.setStatus(SpanStatus.Internal("Error"))).as(ExitCode.Success)
  }
}
