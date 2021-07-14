package io.janstenpickle.trace4cats.stackdriver.oauth

/** Code adapted from https://github.com/permutive/fs2-google-pubsub
  */
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.time.Instant
import java.util.Date

import cats.effect.Sync
import cats.syntax.all._
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.typelevel.log4cats.Logger
import org.http4s.Method.POST
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s._
import org.http4s.circe.CirceEntityCodec._

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

class GoogleOAuth[F[_]: Logger](key: RSAPrivateKey, httpClient: Client[F])(implicit F: Sync[F])
    extends OAuth[F]
    with Http4sClientDsl[F] {
  import GoogleOAuth._

  final private[this] val algorithm = Algorithm.RSA256(null: RSAPublicKey, key)
  final private[this] val googleOAuthDomainStr = "https://www.googleapis.com/oauth2/v4/token"
  final private[this] val googleOAuthDomain = Uri.unsafeFromString(googleOAuthDomainStr)

  final override def authenticate(iss: String, scope: String, exp: Instant, iat: Instant): F[Option[AccessToken]] = {
    val tokenF = F.delay(
      JWT.create
        .withIssuedAt(Date.from(iat))
        .withExpiresAt(Date.from(exp))
        .withAudience(googleOAuthDomainStr)
        .withClaim("scope", scope)
        .withClaim("iss", iss)
        .sign(algorithm)
    )

    val request =
      for {
        token <- tokenF
        form = UrlForm("grant_type" -> "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion" -> token)
        req = POST(form, googleOAuthDomain)
      } yield req

    httpClient
      .expectOr[Option[AccessToken]](request) { resp =>
        resp.as[String].map(FailedRequest.apply)
      }
      .handleErrorWith { e =>
        Logger[F].warn(e)("Failed to retrieve JWT Access Token from Google").as(None)
      }
  }

  final override val maxDuration: FiniteDuration = 1.hour
}

object GoogleOAuth {
  case class FailedRequest(body: String)
      extends RuntimeException(s"Failed request, got response: $body")
      with NoStackTrace
}
