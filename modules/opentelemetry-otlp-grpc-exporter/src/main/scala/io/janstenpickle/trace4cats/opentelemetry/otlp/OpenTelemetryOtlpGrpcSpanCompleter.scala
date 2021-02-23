package io.janstenpickle.trace4cats.opentelemetry.otlp

import cats.effect.{Concurrent, Resource, Timer}
import fs2.Chunk
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.QueuedSpanCompleter
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess

import scala.concurrent.duration._

object OpenTelemetryOtlpGrpcSpanCompleter {
  def apply[F[_]: Concurrent: Timer](
    process: TraceProcess,
    host: String = "localhost",
    port: Int = 55680,
    bufferSize: Int = 2000,
    batchSize: Int = 50,
    batchTimeout: FiniteDuration = 10.seconds
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- OpenTelemetryOtlpGrpcSpanExporter[F, Chunk](host, port)
      completer <- QueuedSpanCompleter[F](process, exporter, bufferSize, batchSize, batchTimeout)
    } yield completer
}
