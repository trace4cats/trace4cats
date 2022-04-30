package trace4cats.rate

import cats.Functor
import cats.effect.kernel.syntax.spawn._
import cats.effect.kernel.{Ref, Resource, Temporal}
import cats.syntax.functor._
import fs2.Stream

import scala.concurrent.duration.FiniteDuration

trait TokenBucket[F[_]] {
  def request1: F[Boolean]
  def request(n: Int): F[Int]
}

object TokenBucket {
  def apply[F[_]](implicit tokenBucket: TokenBucket[F]): TokenBucket[F] = tokenBucket

  def create[F[_]: Temporal](bucketSize: Int, tokenRate: FiniteDuration): Resource[F, TokenBucket[F]] =
    for {
      tokens <- Resource.eval(Ref.of(bucketSize))
      _ <- bucketProcess(tokens, bucketSize, tokenRate).background
    } yield impl(tokens)

  private[rate] def bucketProcess[F[_]: Temporal](
    tokens: Ref[F, Int],
    bucketSize: Int,
    tokenRate: FiniteDuration
  ): F[Unit] =
    Stream
      .fixedRate[F](tokenRate)
      .evalMap(_ =>
        tokens.update { current =>
          if (current == bucketSize) current
          else current + 1
        }
      )
      .compile
      .drain

  private[rate] def impl[F[_]: Functor](tokens: Ref[F, Int]): TokenBucket[F] = new TokenBucket[F] {
    override def request1: F[Boolean] =
      tokens
        .getAndUpdate { current =>
          if (current == 0) 0
          else current - 1
        }
        .map(_ != 0)

    override def request(n: Int): F[Int] =
      tokens
        .getAndUpdate { current =>
          if (current >= n) current - n
          else 0
        }
        .map { tokens =>
          if (tokens >= n) n
          else tokens
        }
  }
}
