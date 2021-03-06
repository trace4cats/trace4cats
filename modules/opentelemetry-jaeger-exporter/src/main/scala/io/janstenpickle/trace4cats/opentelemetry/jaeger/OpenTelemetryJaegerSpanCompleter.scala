package io.janstenpickle.trace4cats.opentelemetry.jaeger

import cats.effect.kernel.{Async, Resource}
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, QueuedSpanCompleter}
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess

import scala.concurrent.ExecutionContext

object OpenTelemetryJaegerSpanCompleter {
  def apply[F[_]: Async](
    process: TraceProcess,
    host: String = "localhost",
    port: Int = 14250,
    config: CompleterConfig = CompleterConfig(),
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.eval(Slf4jLogger.create[F])
      exporter <- OpenTelemetryJaegerSpanExporter[F, Chunk](host, port, ec)
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer
}
