package io.janstenpickle.trace4cats.strackdriver

import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Timer}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.QueuedSpanCompleter
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess
import org.http4s.client.Client

import scala.concurrent.duration._

object StackdriverHttpSpanCompleter {
  def emberClient[F[_]: Concurrent: ContextShift: Timer](
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
      exporter <- StackdriverHttpSpanExporter.emberClient[F](blocker, projectId, serviceAccountPath)
      completer <- QueuedSpanCompleter[F](process, exporter, bufferSize, batchSize, batchTimeout)
    } yield completer

  def apply[F[_]: Concurrent: Timer](
    process: TraceProcess,
    projectId: String,
    serviceAccountPath: String,
    client: Client[F],
    bufferSize: Int = 2000,
    batchSize: Int = 50,
    batchTimeout: FiniteDuration = 10.seconds
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- Resource.liftF(StackdriverHttpSpanExporter[F](projectId, serviceAccountPath, client))
      completer <- QueuedSpanCompleter[F](process, exporter, bufferSize, batchSize, batchTimeout)
    } yield completer

}
