package io.janstenpickle.trace4cats.stackdriver

import cats.Foldable
import cats.effect.kernel.{Async, Resource, Temporal}
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.foldable._
import org.typelevel.log4cats.Logger
import io.janstenpickle.trace4cats.`export`.HttpSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch
import io.janstenpickle.trace4cats.stackdriver.oauth.{
  CachedTokenProvider,
  InstanceMetadataTokenProvider,
  OAuthTokenProvider,
  TokenProvider
}
import io.janstenpickle.trace4cats.stackdriver.project.{
  InstanceMetadataProjectIdProvider,
  ProjectIdProvider,
  StaticProjectIdProvider
}
import org.http4s.Uri
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext

object StackdriverHttpSpanExporter {
  private final val base = "https://cloudtrace.googleapis.com/v2/projects"

  def serviceAccountBlazeClient[F[_]: Async: Logger, G[_]: Foldable](
    projectId: String,
    serviceAccountPath: String,
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanExporter[F, G]] = for {
    ec <- Resource.eval(ec.fold(Async[F].executionContext)(_.pure))
    client <- BlazeClientBuilder[F](ec).resource
    exporter <- Resource.eval(apply[F, G](projectId, serviceAccountPath, client))
  } yield exporter

  def blazeClient[F[_]: Async: Logger, G[_]: Foldable](
    serviceAccountName: String = "default",
    ec: Option[ExecutionContext] = None
  ): Resource[F, SpanExporter[F, G]] = for {
    ec <- Resource.eval(ec.fold(Async[F].executionContext)(_.pure))
    client <- BlazeClientBuilder[F](ec).resource
    exporter <- Resource.eval(apply[F, G](client, serviceAccountName))
  } yield exporter

  def apply[F[_]: Async: Logger, G[_]: Foldable](
    projectId: String,
    serviceAccountPath: String,
    client: Client[F]
  ): F[SpanExporter[F, G]] =
    OAuthTokenProvider[F](serviceAccountPath, client).flatMap { tokenProvider =>
      apply[F, G](StaticProjectIdProvider(projectId), tokenProvider, client)
    }

  def apply[F[_]: Temporal: Logger, G[_]: Foldable](
    client: Client[F],
    serviceAccountName: String = "default"
  ): F[SpanExporter[F, G]] =
    apply[F, G](
      InstanceMetadataProjectIdProvider(client),
      InstanceMetadataTokenProvider(client, serviceAccountName),
      client
    )

  def apply[F[_]: Temporal, G[_]: Foldable](
    projectIdProvider: ProjectIdProvider[F],
    tokenProvider: TokenProvider[F],
    client: Client[F]
  ): F[SpanExporter[F, G]] =
    for {
      cachedTokenProvider <- CachedTokenProvider(tokenProvider)
      projectId <- projectIdProvider.projectId
      exporter <- HttpSpanExporter[F, G, model.Batch](
        client,
        s"$base/$projectId/traces:batchWrite",
        (batch: Batch[G]) =>
          model.Batch(
            batch.spans
              .foldLeft(ListBuffer.empty[model.Span]) { (buf, span) =>
                buf += model.Span.fromCompleted(projectId, span)
              }
              .toList
          ),
        (uri: Uri) =>
          cachedTokenProvider.accessToken.map { token =>
            uri.withQueryParam("access_token", token.accessToken)
          }
      )
    } yield exporter
}
