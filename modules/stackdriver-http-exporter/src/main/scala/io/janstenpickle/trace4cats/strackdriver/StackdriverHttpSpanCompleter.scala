package io.janstenpickle.trace4cats.strackdriver

import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, Resource, Timer}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.QueuedSpanCompleter
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess
import io.janstenpickle.trace4cats.strackdriver.oauth.TokenProvider
import io.janstenpickle.trace4cats.strackdriver.project.ProjectIdProvider
import org.http4s.client.Client

import scala.concurrent.duration._

object StackdriverHttpSpanCompleter {
  def serviceAccountBlazeClient[F[_]: ConcurrentEffect: ContextShift: Timer](
    blocker: Blocker,
    process: TraceProcess,
    projectId: String,
    serviceAccountPath: String,
    bufferSize: Int = 2000,
    batchSize: Int = 50,
    batchTimeout: FiniteDuration = 10.seconds
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- StackdriverHttpSpanExporter.blazeClient[F](blocker, projectId, serviceAccountPath)
      completer <- QueuedSpanCompleter[F](process, exporter, bufferSize, batchSize, batchTimeout)
    } yield completer

  def blazeClient[F[_]: ConcurrentEffect: ContextShift: Timer](
    blocker: Blocker,
    process: TraceProcess,
    serviceAccountName: String = "default",
    bufferSize: Int = 2000,
    batchSize: Int = 50,
    batchTimeout: FiniteDuration = 10.seconds
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- StackdriverHttpSpanExporter.blazeClient[F](blocker, serviceAccountName)
      completer <- QueuedSpanCompleter[F](process, exporter, bufferSize, batchSize, batchTimeout)
    } yield completer

  def serviceAccount[F[_]: Concurrent: Timer](
    process: TraceProcess,
    client: Client[F],
    projectId: String,
    serviceAccountPath: String,
    bufferSize: Int = 2000,
    batchSize: Int = 50,
    batchTimeout: FiniteDuration = 10.seconds
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- Resource.liftF(StackdriverHttpSpanExporter[F](projectId, serviceAccountPath, client))
      completer <- QueuedSpanCompleter[F](process, exporter, bufferSize, batchSize, batchTimeout)
    } yield completer

  def apply[F[_]: Concurrent: Timer](
    process: TraceProcess,
    client: Client[F],
    serviceAccountName: String = "default",
    bufferSize: Int = 2000,
    batchSize: Int = 50,
    batchTimeout: FiniteDuration = 10.seconds
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- Resource.liftF(StackdriverHttpSpanExporter[F](client, serviceAccountName))
      completer <- QueuedSpanCompleter[F](process, exporter, bufferSize, batchSize, batchTimeout)
    } yield completer

  def fromProviders[F[_]: Concurrent: Timer](
    process: TraceProcess,
    projectIdProvider: ProjectIdProvider[F],
    tokenProvider: TokenProvider[F],
    client: Client[F],
    bufferSize: Int = 2000,
    batchSize: Int = 50,
    batchTimeout: FiniteDuration = 10.seconds
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- Resource.liftF(StackdriverHttpSpanExporter[F](projectIdProvider, tokenProvider, client))
      completer <- QueuedSpanCompleter[F](process, exporter, bufferSize, batchSize, batchTimeout)
    } yield completer
}
