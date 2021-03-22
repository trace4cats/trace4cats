package io.janstenpickle.trace4cats.example

import cats.data.NonEmptySet
import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource}
import cats.syntax.semigroup._
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, QueuedSpanCompleter}
import io.janstenpickle.trace4cats.avro.AvroSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SpanKind, SpanStatus, TraceProcess}
import io.janstenpickle.trace4cats.rate.sampling.RateTailSpanSampler
import io.janstenpickle.trace4cats.sampling.tail.cache.LocalCacheSampleDecisionStore
import io.janstenpickle.trace4cats.sampling.tail.{TailSamplingSpanExporter, TailSpanSampler}

import scala.concurrent.duration._

object TailSampling extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    (for {
      blocker <- Blocker[IO]
      implicit0(logger: Logger[IO]) <- Resource.eval(Slf4jLogger.create[IO])
      exporter <- AvroSpanExporter.udp[IO, Chunk](blocker)

      nameSampleDecisionStore <-
        Resource.eval(LocalCacheSampleDecisionStore[IO](ttl = 10.minutes, maximumSize = Some(200000)))
      rateSampleDecisionStore <-
        Resource.eval(LocalCacheSampleDecisionStore[IO](ttl = 10.minutes, maximumSize = Some(200000)))

      probSampler = TailSpanSampler.probabilistic[IO, Chunk](probability = 0.05)
      nameSampler =
        TailSpanSampler
          .spanNameDrop[IO, Chunk](nameSampleDecisionStore, NonEmptySet.of("/healthcheck", "/readiness", "/metrics"))
      rateSampler <-
        RateTailSpanSampler.create[IO, Chunk](rateSampleDecisionStore, bucketSize = 100, tokenRate = 100.millis)

      combinedSampler =
        probSampler |+| nameSampler |+| rateSampler // TailSpanSampler.combined may also be used to combine two samplers

      samplingExporter = TailSamplingSpanExporter(exporter, combinedSampler)

      completer <- QueuedSpanCompleter[IO](TraceProcess("trace4cats"), samplingExporter, config = CompleterConfig())

      root <- Span.root[IO]("root", SpanKind.Client, SpanSampler.always, completer)
      child <- root.child("child", SpanKind.Server)
    } yield child).use(_.setStatus(SpanStatus.Internal("Error"))).as(ExitCode.Success)
}
