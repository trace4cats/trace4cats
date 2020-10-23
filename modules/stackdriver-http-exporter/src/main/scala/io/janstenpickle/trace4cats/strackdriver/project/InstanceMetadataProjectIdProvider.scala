package io.janstenpickle.trace4cats.strackdriver.project

import cats.effect.Sync
import org.http4s.Method.GET
import org.http4s.{Header, Uri}
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl

object InstanceMetadataProjectIdProvider {
  final private[this] val projectIdMetadataUri =
    Uri.unsafeFromString("http://metadata.google.internal/computeMetadata/v1/project/project-id")
  final private[this] val metadataHeader = Header("Metadata-Flavor", "Google")

  def apply[F[_]: Sync](httpClient: Client[F]): ProjectIdProvider[F] =
    new ProjectIdProvider[F] with Http4sClientDsl[F] {
      override val projectId: F[String] = httpClient.expect[String](GET(projectIdMetadataUri, metadataHeader))
    }
}
