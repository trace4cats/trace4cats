package io.janstenpickle.trace4cats.stackdriver.oauth

import cats.effect.kernel.{Clock, Ref, Temporal}
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.applicative._

import scala.concurrent.duration._

object CachedTokenProvider {
  def apply[F[_]: Temporal](
    underlying: TokenProvider[F],
    expiryOffset: FiniteDuration = 10.seconds
  ): F[TokenProvider[F]] =
    Ref.of[F, Option[(FiniteDuration, AccessToken)]](None).map { ref =>
      new TokenProvider[F] {
        private def refreshToken =
          underlying.accessToken.flatTap { token =>
            Clock[F].realTime.flatMap { now =>
              ref.set(Some((now + token.expiresIn.seconds - expiryOffset, token)))
            }
          }

        override def accessToken: F[AccessToken] =
          ref.get.flatMap {
            case None => refreshToken
            case Some((expiresAt, token)) =>
              for {
                now <- Clock[F].realTime
                t <-
                  if (now < expiresAt) token.copy(expiresIn = (expiresAt - now).toSeconds).pure[F]
                  else refreshToken
              } yield t
          }
      }
    }
}
