package io.janstenpickle.trace4cats.strackdriver.oauth

/** Code adapted from https://github.com/permutive/fs2-google-pubsub
  */
import java.io.File
import java.time.Instant

import cats.effect.Sync
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import org.http4s.client.Client

class OAuthTokenProvider[F[_]](emailAddress: String, scope: List[String], auth: OAuth[F])(implicit F: Sync[F])
    extends TokenProvider[F] {
  override val accessToken: F[AccessToken] = {
    for {
      now <- F.delay(Instant.now())
      token <- auth.authenticate(emailAddress, scope.mkString(","), now.plusMillis(auth.maxDuration.toMillis), now)
      tokenOrError <- token.fold(F.raiseError[AccessToken](TokenProvider.FailedToGetToken))(_.pure[F])
    } yield tokenOrError
  }
}

object OAuthTokenProvider {
  def apply[F[_]: Logger](serviceAccountPath: String, httpClient: Client[F])(implicit
    F: Sync[F]
  ): F[OAuthTokenProvider[F]] =
    for {
      serviceAccount <- F.fromEither(GoogleAccountParser.parse(new File(serviceAccountPath).toPath))
    } yield new OAuthTokenProvider(
      serviceAccount.clientEmail,
      List("https://www.googleapis.com/auth/trace.append"),
      new GoogleOAuth(serviceAccount.privateKey, httpClient)
    )

  def noAuth[F[_]: Sync]: OAuthTokenProvider[F] =
    new OAuthTokenProvider("noop", Nil, new NoopOAuth)
}
