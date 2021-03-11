package io.janstenpickle.trace4cats.stackdriver.oauth

/** Code adapted from https://github.com/permutive/fs2-google-pubsub
  */
import java.io.File
import cats.effect.{MonadThrow, Sync}
import cats.effect.kernel.{Async, Clock}
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import org.http4s.client.Client

class OAuthTokenProvider[F[_]: MonadThrow: Clock](emailAddress: String, scope: List[String], auth: OAuth[F])
    extends TokenProvider[F] {
  override val accessToken: F[AccessToken] = {
    for {
      now <- Clock[F].realTimeInstant
      token <- auth.authenticate(emailAddress, scope.mkString(","), now.plusMillis(auth.maxDuration.toMillis), now)
      tokenOrError <- token.liftTo[F](TokenProvider.FailedToGetToken)
    } yield tokenOrError
  }
}

object OAuthTokenProvider {
  def apply[F[_]: Async: Logger](serviceAccountPath: String, httpClient: Client[F]): F[OAuthTokenProvider[F]] =
    for {
      serviceAccount <- Async[F].fromEither(GoogleAccountParser.parse(new File(serviceAccountPath).toPath))
    } yield new OAuthTokenProvider(
      serviceAccount.clientEmail,
      List("https://www.googleapis.com/auth/trace.append"),
      new GoogleOAuth(serviceAccount.privateKey, httpClient)
    )

  def noAuth[F[_]: Sync]: OAuthTokenProvider[F] =
    new OAuthTokenProvider("noop", Nil, new NoopOAuth)
}
