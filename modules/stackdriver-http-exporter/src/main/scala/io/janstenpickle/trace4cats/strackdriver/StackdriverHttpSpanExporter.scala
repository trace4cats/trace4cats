package io.janstenpickle.trace4cats.strackdriver

import cats.effect.{Blocker, Concurrent, ConcurrentEffect, ContextShift, Resource, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import io.janstenpickle.trace4cats.strackdriver.oauth.{
  CachedTokenProvider,
  InstanceMetadataTokenProvider,
  OAuthTokenProvider,
  TokenProvider
}
import io.janstenpickle.trace4cats.strackdriver.project.{
  InstanceMetadataProjectIdProvider,
  ProjectIdProvider,
  StaticProjectIdProvider
}
import org.http4s.Uri
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

object StackdriverHttpSpanExporter {
  private final val base = "https://cloudtrace.googleapis.com/v2/projects"

  def blazeClient[F[_]: ConcurrentEffect: Timer: ContextShift: Logger](
    blocker: Blocker,
    projectId: String,
    serviceAccountPath: String,
  ): Resource[F, SpanExporter[F]] =
    BlazeClientBuilder[F](blocker.blockingContext).resource.evalMap(apply[F](projectId, serviceAccountPath, _))

  def blazeClient[F[_]: ConcurrentEffect: Timer: ContextShift: Logger](
    blocker: Blocker,
    serviceAccountName: String = "default"
  ): Resource[F, SpanExporter[F]] =
    BlazeClientBuilder[F](blocker.blockingContext).resource.evalMap(apply[F](_, serviceAccountName))

  def apply[F[_]: Concurrent: Timer: Logger](
    projectId: String,
    serviceAccountPath: String,
    client: Client[F]
  ): F[SpanExporter[F]] =
    OAuthTokenProvider[F](serviceAccountPath, client).flatMap { tokenProvider =>
      apply[F](StaticProjectIdProvider(projectId), tokenProvider, client)
    }

  def apply[F[_]: Concurrent: Timer: Logger](
    client: Client[F],
    serviceAccountName: String = "default"
  ): F[SpanExporter[F]] =
    apply[F](
      InstanceMetadataProjectIdProvider(client),
      InstanceMetadataTokenProvider(client, serviceAccountName),
      client
    )

  def apply[F[_]: Concurrent: Timer: Logger](
    projectIdProvider: ProjectIdProvider[F],
    tokenProvider: TokenProvider[F],
    client: Client[F]
  ): F[SpanExporter[F]] =
    for {
      cachedTokenProvider <- CachedTokenProvider(tokenProvider)
      projectId <- projectIdProvider.projectId
      exporter <- HttpSpanExporter[F, model.Batch](
        client,
        s"$base/$projectId/traces:batchWrite",
        (batch: Batch) => model.Batch(batch.spans.map(model.Span.fromCompleted(projectId, _))),
        (uri: Uri) =>
          cachedTokenProvider.accessToken.map { token =>
            uri.withQueryParam("access_token", token.accessToken)
          }
      )
    } yield exporter
}
