package io.janstenpickle.trace4cats.strackdriver.oauth

/**
Code adapted from https://github.com/permutive/fs2-google-pubsub
 **/
import scala.util.control.NoStackTrace

trait TokenProvider[F[_]] {
  def accessToken: F[AccessToken]
}

object TokenProvider {
  case object TokenValidityTooLong
      extends RuntimeException("Valid for duration cannot be longer than maximum of the OAuth provider")
      with NoStackTrace

  case object FailedToGetToken extends RuntimeException("Failed to get token after many attempts")

  def apply[F[_]](implicit tokenProvider: TokenProvider[F]): TokenProvider[F] = tokenProvider
}
