package io.janstenpickle.trace4cats.strackdriver

import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import io.janstenpickle.trace4cats.strackdriver.http.CloudTraceClient
import io.janstenpickle.trace4cats.strackdriver.oauth.DefaultTokenProvider
import org.http4s.client.Client
import org.http4s.client.middleware.GZip
import org.http4s.ember.client.EmberClientBuilder

object StackdriverHttpSpanExporter {
  def emberClient[F[_]: Concurrent: Timer: ContextShift: Logger](
    blocker: Blocker,
    projectId: String,
    serviceAccountPath: String
  ): Resource[F, SpanExporter[F]] =
    EmberClientBuilder
      .default[F]
      .withLogger(Logger[F])
      .withBlocker(blocker)
      .build
      .evalMap(apply[F](projectId, serviceAccountPath, _))

  def apply[F[_]: Concurrent: Logger](
    projectId: String,
    serviceAccountPath: String,
    client: Client[F]
  ): F[SpanExporter[F]] =
    for {
      tokenProvider <- DefaultTokenProvider.google(serviceAccountPath, client)
      traceClient <- CloudTraceClient[F](projectId, GZip()(client), tokenProvider.accessToken)
    } yield
      new SpanExporter[F] {
        override def exportBatch(batch: Batch): F[Unit] = {
          val traceBatch = model.Batch(batch.spans.map(model.Span.fromCompleted(projectId, batch.process, _)))
          traceClient.submitBatch(traceBatch)
        }
      }
}
