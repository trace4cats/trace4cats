package io.janstenpickle.trace4cats.example

import cats.data.NonEmptySet
import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import fs2.Chunk
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.`export`.QueuedSpanCompleter
import io.janstenpickle.trace4cats.avro.AvroSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SpanKind, SpanStatus, TraceProcess}
import io.janstenpickle.trace4cats.sampling.tail.cache.LocalCacheSampleDecisionStore
import io.janstenpickle.trace4cats.sampling.tail.{TailSamplingSpanExporter, TailSpanSampler}

import scala.concurrent.duration._

object TailSampling extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    (for {
      blocker <- Blocker[IO]
      implicit0(logger: Logger[IO]) <- Resource.liftF(Slf4jLogger.create[IO])
      exporter <- AvroSpanExporter.udp[IO, Chunk](blocker)

      sampleDecisionStore <-
        Resource.liftF(LocalCacheSampleDecisionStore[IO](ttl = 10.minutes, maximumSize = Some(200000)))

      probSampler = TailSpanSampler.probabilistic[IO, Chunk](probability = 0.05)
      nameSampler =
        TailSpanSampler
          .spanNameFilter[IO, Chunk](sampleDecisionStore, NonEmptySet.of("/healthcheck", "/readiness", "/metrics"))
      combinedSampler = TailSpanSampler.combined(probSampler, nameSampler) // a semigroup instance is also available

      samplingExporter = TailSamplingSpanExporter(exporter, combinedSampler)

      completer <- QueuedSpanCompleter[IO](
        TraceProcess("trace4cats"),
        samplingExporter,
        bufferSize = 2000,
        batchSize = 50,
        batchTimeout = 10.seconds
      )

      root <- Span.root[IO]("root", SpanKind.Client, SpanSampler.always, completer)
      child <- root.child("child", SpanKind.Server)
    } yield child).use(_.setStatus(SpanStatus.Internal("Error"))).as(ExitCode.Success)
}
