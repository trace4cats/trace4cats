package io.janstenpickle.trace4cats.strackdriver

import cats.effect.{Blocker, Concurrent, ConcurrentEffect, Resource, Timer}
import fs2.Chunk
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, QueuedSpanCompleter}
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess
import io.janstenpickle.trace4cats.strackdriver.oauth.TokenProvider
import io.janstenpickle.trace4cats.strackdriver.project.ProjectIdProvider
import org.http4s.client.Client

object StackdriverHttpSpanCompleter {
  def serviceAccountBlazeClient[F[_]: ConcurrentEffect: Timer](
    blocker: Blocker,
    process: TraceProcess,
    projectId: String,
    serviceAccountPath: String,
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- StackdriverHttpSpanExporter.blazeClient[F, Chunk](blocker, projectId, serviceAccountPath)
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer

  def blazeClient[F[_]: ConcurrentEffect: Timer](
    blocker: Blocker,
    process: TraceProcess,
    serviceAccountName: String = "default",
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- StackdriverHttpSpanExporter.blazeClient[F, Chunk](blocker, serviceAccountName)
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
