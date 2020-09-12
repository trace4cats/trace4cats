package io.janstenpickle.trace4cats.newrelic

import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, Resource, Timer}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.QueuedSpanCompleter
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceProcess
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.duration._

object NewRelicSpanCompleter {
  def blazeClient[F[_]: ConcurrentEffect: Timer: ContextShift](
    blocker: Blocker,
    process: TraceProcess,
    apiKey: String,
    endpoint: Endpoint,
    bufferSize: Int = 2000,
    batchSize: Int = 50,
    batchTimeout: FiniteDuration = 10.seconds
  ): Resource[F, SpanCompleter[F]] =
    BlazeClientBuilder[F](blocker.blockingContext).resource
      .flatMap(apply[F](_, process, apiKey, endpoint, bufferSize, batchSize, batchTimeout))

  def apply[F[_]: Concurrent: ContextShift: Timer](
    client: Client[F],
    process: TraceProcess,
    apiKey: String,
    endpoint: Endpoint,
    bufferSize: Int = 2000,
    batchSize: Int = 50,
    batchTimeout: FiniteDuration = 10.seconds
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- Resource.liftF(NewRelicSpanExporter[F](client, apiKey, endpoint))
      completer <- QueuedSpanCompleter[F](process, exporter, bufferSize, batchSize, batchTimeout)
    } yield completer
}
