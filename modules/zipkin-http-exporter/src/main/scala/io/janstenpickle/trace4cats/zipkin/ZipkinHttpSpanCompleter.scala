package io.janstenpickle.trace4cats.zipkin

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

object ZipkinHttpSpanCompleter {

  def blazeClient[F[_]: ConcurrentEffect: Timer](
    process: TraceProcess,
    host: String = "localhost",
    port: Int = 9411,
    config: CompleterConfig = CompleterConfig(),
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanCompleter[F]] = for {
    client <- BlazeClientBuilder[F](ec.getOrElse(ExecutionContext.global)).resource
    completer <- apply[F](client, process, host, port, config)
  } yield completer

  def apply[F[_]: Concurrent: Timer](
    client: Client[F],
    process: TraceProcess,
    host: String = "localhost",
    port: Int = 9411,
    config: CompleterConfig = CompleterConfig()
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.eval(Slf4jLogger.create[F])
      exporter <- Resource.eval(ZipkinHttpSpanExporter[F, Chunk](client, host, port))
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer

  def apply[F[_]: Concurrent: Timer](
    client: Client[F],
    process: TraceProcess,
    uri: String,
    config: CompleterConfig
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.eval(Slf4jLogger.create[F])
      exporter <- Resource.eval(ZipkinHttpSpanExporter[F, Chunk](client, uri))
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer
}
