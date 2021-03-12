package io.janstenpickle.trace4cats.stackdriver

import cats.effect.{Concurrent, ConcurrentEffect, Resource, Timer}
import fs2.Chunk
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, QueuedSpanCompleter}
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess
import io.janstenpickle.trace4cats.stackdriver.oauth.TokenProvider
import io.janstenpickle.trace4cats.stackdriver.project.ProjectIdProvider
import org.http4s.client.Client
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext

object StackdriverHttpSpanCompleter {
  def serviceAccountBlazeClient[F[_]: ConcurrentEffect: Timer](
    process: TraceProcess,
    projectId: String,
    serviceAccountPath: String,
    config: CompleterConfig = CompleterConfig(),
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- StackdriverHttpSpanExporter.blazeClient[F, Chunk](projectId, serviceAccountPath, ec)
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer

  def blazeClient[F[_]: ConcurrentEffect: Timer](
    process: TraceProcess,
    serviceAccountName: String = "default",
    config: CompleterConfig = CompleterConfig(),
    ec: Option[ExecutionContext] = None,
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- StackdriverHttpSpanExporter.blazeClient[F, Chunk](serviceAccountName, ec)
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer

  def serviceAccount[F[_]: Concurrent: Timer](
    process: TraceProcess,
    client: Client[F],
    projectId: String,
    serviceAccountPath: String,
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- Resource.liftF(StackdriverHttpSpanExporter[F, Chunk](projectId, serviceAccountPath, client))
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer

  def apply[F[_]: Concurrent: Timer](
    process: TraceProcess,
    client: Client[F],
    serviceAccountName: String = "default",
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- Resource.liftF(StackdriverHttpSpanExporter[F, Chunk](client, serviceAccountName))
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer

  def fromProviders[F[_]: Concurrent: Timer](
    process: TraceProcess,
    projectIdProvider: ProjectIdProvider[F],
    tokenProvider: TokenProvider[F],
    client: Client[F],
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- Resource.liftF(StackdriverHttpSpanExporter[F, Chunk](projectIdProvider, tokenProvider, client))
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer
}
