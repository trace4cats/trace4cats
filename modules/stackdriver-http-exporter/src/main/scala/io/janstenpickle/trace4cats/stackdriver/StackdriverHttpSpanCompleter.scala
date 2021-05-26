package io.janstenpickle.trace4cats.stackdriver

import cats.effect.kernel.{Async, Resource}
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, QueuedSpanCompleter}
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanExporter}
import io.janstenpickle.trace4cats.model.TraceProcess
import io.janstenpickle.trace4cats.stackdriver.oauth.TokenProvider
import io.janstenpickle.trace4cats.stackdriver.project.ProjectIdProvider
import org.http4s.client.Client

import scala.concurrent.ExecutionContext

object StackdriverHttpSpanCompleter {
  def serviceAccountBlazeClient[F[_]: Async](
    process: TraceProcess,
    projectId: String,
    serviceAccountPath: String,
    config: CompleterConfig = CompleterConfig(),
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanCompleter[F]] = makeSpanCompleter[F](process, config)(implicit logger =>
    StackdriverHttpSpanExporter.serviceAccountBlazeClient[F, Chunk](projectId, serviceAccountPath, ec)
  )

  def blazeClient[F[_]: Async](
    process: TraceProcess,
    serviceAccountName: String = "default",
    config: CompleterConfig = CompleterConfig(),
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanCompleter[F]] = makeSpanCompleter[F](process, config)(implicit logger =>
    StackdriverHttpSpanExporter.blazeClient[F, Chunk](serviceAccountName, ec)
  )

  def serviceAccount[F[_]: Async](
    process: TraceProcess,
    client: Client[F],
    projectId: String,
    serviceAccountPath: String,
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] = makeSpanCompleter[F](process, config)(implicit logger =>
    Resource.eval(StackdriverHttpSpanExporter[F, Chunk](projectId, serviceAccountPath, client))
  )

  def apply[F[_]: Async](
    process: TraceProcess,
    client: Client[F],
    serviceAccountName: String = "default",
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] = makeSpanCompleter[F](process, config)(implicit logger =>
    Resource.eval(StackdriverHttpSpanExporter[F, Chunk](client, serviceAccountName))
  )

  def fromProviders[F[_]: Async](
    process: TraceProcess,
    projectIdProvider: ProjectIdProvider[F],
    tokenProvider: TokenProvider[F],
    client: Client[F],
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] = makeSpanCompleter[F](process, config)(_ =>
    Resource.eval(StackdriverHttpSpanExporter[F, Chunk](projectIdProvider, tokenProvider, client))
  )

  private def makeSpanCompleter[F[_]: Async](process: TraceProcess, config: CompleterConfig)(
    exporter: Logger[F] => Resource[F, SpanExporter[F, Chunk]]
  ): Resource[F, SpanCompleter[F]] = {
    Resource
      .eval(Slf4jLogger.create[F])
      .flatMap { implicit logger: Logger[F] =>
        exporter(logger).flatMap(QueuedSpanCompleter[F](process, _, config))
      }
  }
}
