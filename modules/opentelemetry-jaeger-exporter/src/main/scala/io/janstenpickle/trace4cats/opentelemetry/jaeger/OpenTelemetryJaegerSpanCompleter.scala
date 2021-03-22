package io.janstenpickle.trace4cats.opentelemetry.jaeger

import cats.effect.{Concurrent, Resource, Timer}
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, QueuedSpanCompleter}
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess

object OpenTelemetryJaegerSpanCompleter {
  def apply[F[_]: Concurrent: Timer](
    process: TraceProcess,
    host: String = "localhost",
    port: Int = 14250,
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.eval(Slf4jLogger.create[F])
      exporter <- OpenTelemetryJaegerSpanExporter[F, Chunk](host, port)
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer
}
