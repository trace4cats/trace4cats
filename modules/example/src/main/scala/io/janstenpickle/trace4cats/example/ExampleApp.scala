package io.janstenpickle.trace4cats.example

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.avro.AvroSpanCompleter
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SpanKind, SpanStatus, TraceProcess}

import scala.concurrent.duration._

object ExampleApp extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    (for {
      blocker <- Blocker[IO]
      completer <- AvroSpanCompleter.tcp[IO](blocker, TraceProcess("test"), batchTimeout = 50.millis)
      root <- Span.root[IO]("root", SpanKind.Client, SpanSampler.always, completer)
      child <- root.child("child", SpanKind.Server)
    } yield child).use(_.setStatus(SpanStatus.Internal)).as(ExitCode.Success)
}
