package io.janstenpickle.trace4cats.opentelemetry.otlp

import cats.effect.{Concurrent, Resource, Timer}
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, QueuedSpanCompleter}
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess

object OpenTelemetryOtlpGrpcSpanCompleter {
  def apply[F[_]: Concurrent: Timer](
    process: TraceProcess,
    host: String = "localhost",
    port: Int = 55680,
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- OpenTelemetryOtlpGrpcSpanExporter[F, Chunk](host, port)
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer
}
