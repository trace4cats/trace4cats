package io.janstenpickle.trace4cats.stackdriver

import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Timer}
import com.google.auth.Credentials
import fs2.Chunk
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.QueuedSpanCompleter
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model._

import scala.concurrent.duration._

object StackdriverGrpcSpanCompleter {
  def apply[F[_]: Concurrent: ContextShift: Timer](
    blocker: Blocker,
    process: TraceProcess,
    projectId: String,
    credentials: Option[Credentials] = None,
    requestTimeout: FiniteDuration = 5.seconds,
    bufferSize: Int = 2000,
    batchSize: Int = 50,
    batchTimeout: FiniteDuration = 10.seconds
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- StackdriverGrpcSpanExporter[F, Chunk](blocker, projectId, credentials, requestTimeout)
      completer <- QueuedSpanCompleter[F](process, exporter, bufferSize, batchSize, batchTimeout)
    } yield completer
}
