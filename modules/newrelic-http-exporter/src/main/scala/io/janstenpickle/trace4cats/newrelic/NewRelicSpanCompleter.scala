package io.janstenpickle.trace4cats.newrelic

import cats.effect.kernel.{Async, Resource}
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, QueuedSpanCompleter}
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext

object NewRelicSpanCompleter {
  def blazeClient[F[_]: Async](
    ec: ExecutionContext, //TODO: keep parameter or replace with EC.global as recommended?
    process: TraceProcess,
    apiKey: String,
    endpoint: Endpoint,
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] =
    BlazeClientBuilder[F](ec).resource
      .flatMap(apply[F](_, process, apiKey, endpoint, config))

  def apply[F[_]: Async](
    client: Client[F],
    process: TraceProcess,
    apiKey: String,
    endpoint: Endpoint,
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.eval(Slf4jLogger.create[F])
      exporter <- Resource.eval(NewRelicSpanExporter[F, Chunk](client, apiKey, endpoint))
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer
}
