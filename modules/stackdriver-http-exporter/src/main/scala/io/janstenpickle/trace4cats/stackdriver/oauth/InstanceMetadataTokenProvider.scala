package io.janstenpickle.trace4cats.stackdriver.oauth

import cats.effect.Sync
import cats.syntax.applicativeError._
import cats.syntax.functor._
import org.typelevel.log4cats.Logger
import io.janstenpickle.trace4cats.stackdriver.oauth.GoogleOAuth.FailedRequest
import org.http4s.Method.GET
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.Uri

object InstanceMetadataTokenProvider {
  final private[this] val metadataHeader = "Metadata-Flavor" -> "Google"

  def apply[F[_]: Sync: Logger](httpClient: Client[F], serviceAccountName: String = "default"): TokenProvider[F] =
    new TokenProvider[F] with Http4sClientDsl[F] {
      final private[this] val tokenMetadataUri = Uri.unsafeFromString(
        s"http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/$serviceAccountName/token"
      )

      override val accessToken: F[AccessToken] = {
        httpClient
          .expectOr[AccessToken](GET(tokenMetadataUri, metadataHeader)) { resp =>
            resp.as[String].map(FailedRequest.apply)
          }
          .onError { case e =>
            Logger[F].warn(e)("Failed to retrieve Access Token from Instance Metadata")
          }
      }
    }
}
