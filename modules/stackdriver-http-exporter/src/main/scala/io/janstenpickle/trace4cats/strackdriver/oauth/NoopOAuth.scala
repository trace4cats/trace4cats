package io.janstenpickle.trace4cats.strackdriver.oauth

/**
  * Code adapted from https://github.com/permutive/fs2-google-pubsub
  */
import java.time.Instant

import cats.Applicative

import scala.concurrent.duration._

class NoopOAuth[F[_]](implicit F: Applicative[F]) extends OAuth[F] {
  final override def authenticate(iss: String, scope: String, exp: Instant, iat: Instant): F[Option[AccessToken]] =
    F.pure(Some(AccessToken("noop", "noop", 3600)))

  final override val maxDuration: FiniteDuration = 1.hour
}
