package io.janstenpickle.trace4cats.opentelemetry.otlp

import cats.effect.kernel.{Async, Resource}
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, QueuedSpanCompleter}
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess

object OpenTelemetryOtlpGrpcSpanCompleter {
  def apply[F[_]: Async](
    process: TraceProcess,
    host: String = "localhost",
    port: Int = 55680,
    config: CompleterConfig = CompleterConfig()
  ): Resource[F, SpanCompleter[F]] =
    Resource.eval(Slf4jLogger.create[F]).flatMap { implicit logger: Logger[F] =>
      OpenTelemetryOtlpGrpcSpanExporter[F, Chunk](host, port).flatMap(QueuedSpanCompleter[F](process, _, config))
    }
}
