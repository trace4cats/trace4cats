package io.janstenpickle.trace4cats.stackdriver

import cats.effect.{Concurrent, Resource, Timer}
import com.google.auth.Credentials
import fs2.Chunk
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, QueuedSpanCompleter}
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration._

object StackdriverGrpcSpanCompleter {
  def apply[F[_]: Concurrent: Timer](
    process: TraceProcess,
    projectId: String,
    credentials: Option[Credentials] = None,
    requestTimeout: FiniteDuration = 5.seconds,
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- StackdriverGrpcSpanExporter[F, Chunk](projectId, credentials, requestTimeout)
      completer <- QueuedSpanCompleter[F](process, exporter, config)
    } yield completer
}
