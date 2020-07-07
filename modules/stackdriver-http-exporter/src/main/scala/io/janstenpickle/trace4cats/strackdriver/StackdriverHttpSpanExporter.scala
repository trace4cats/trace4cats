package io.janstenpickle.trace4cats.strackdriver

import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import io.janstenpickle.trace4cats.strackdriver.oauth.DefaultTokenProvider
import org.http4s.Uri
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder

object StackdriverHttpSpanExporter {
  private final val base = "https://cloudtrace.googleapis.com/v2/projects"

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

  def apply[F[_]: Concurrent: Timer: Logger](
    projectId: String,
    serviceAccountPath: String,
    client: Client[F]
  ): F[SpanExporter[F]] =
    for {
      tokenProvider <- DefaultTokenProvider.google(serviceAccountPath, client)
      exporter <- HttpSpanExporter[F, model.Batch](
        client,
        s"$base/$projectId/traces:batchWrite",
        (batch: Batch) => model.Batch(batch.spans.map(model.Span.fromCompleted(projectId, batch.process, _))),
        (uri: Uri) =>
          tokenProvider.accessToken.map { token =>
            uri.withQueryParam("access_token", token.accessToken)
        }
      )
    } yield exporter
}
