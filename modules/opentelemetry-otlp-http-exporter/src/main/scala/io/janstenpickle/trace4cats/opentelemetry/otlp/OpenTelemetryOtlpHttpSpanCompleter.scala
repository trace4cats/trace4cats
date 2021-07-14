package io.janstenpickle.trace4cats.opentelemetry.otlp

import cats.effect.{Concurrent, ConcurrentEffect, Resource, Timer}
import fs2.Chunk
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, QueuedSpanCompleter}
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess
import org.http4s.client.Client
import org.http4s.blaze.client.BlazeClientBuilder
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext

object OpenTelemetryOtlpHttpSpanCompleter {
  def blazeClient[F[_]: ConcurrentEffect: Timer](
    process: TraceProcess,
    host: String = "localhost",
    port: Int = 55681,
    config: CompleterConfig = CompleterConfig(),
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanCompleter[F]] =
    // TODO: CE3 - use Async[F].executionContext
    BlazeClientBuilder[F](ec.getOrElse(ExecutionContext.global)).resource
      .flatMap(apply[F](_, process, host, port, config))

  def apply[F[_]: Concurrent: Timer](
    client: Client[F],
    process: TraceProcess,
    host: String = "localhost",
    port: Int = 55681,
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.eval(Slf4jLogger.create[F])
      exporter <- Resource.eval(OpenTelemetryOtlpHttpSpanExporter[F, Chunk](client, host, port))
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer
}
