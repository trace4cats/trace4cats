package io.janstenpickle.trace4cats.stackdriver.oauth

import java.util.concurrent.TimeUnit

import cats.effect.{Clock, Sync}
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.applicative._

import scala.concurrent.duration._
import cats.effect.Ref

object CachedTokenProvider {
  def apply[F[_]: Sync: Clock](
    underlying: TokenProvider[F],
    expiryOffset: FiniteDuration = 10.seconds
  ): F[TokenProvider[F]] =
    Ref.of[F, Option[(Long, AccessToken)]](None).map { ref =>
      new TokenProvider[F] {
        private def refreshToken =
          underlying.accessToken.flatTap { token =>
            Clock[F].realTime(TimeUnit.SECONDS).flatMap { now =>
              ref.set(Some((now + token.expiresIn - expiryOffset.toSeconds, token)))
            }
          }

        override def accessToken: F[AccessToken] =
          ref.get.flatMap {
            case None => refreshToken
            case Some((expiresAt, token)) =>
              for {
                now <- Clock[F].realTime(TimeUnit.SECONDS)
                t <-
                  if (now < expiresAt) token.copy(expiresIn = expiresAt - now).pure[F]
                  else refreshToken
              } yield t
          }
      }
    }
}
