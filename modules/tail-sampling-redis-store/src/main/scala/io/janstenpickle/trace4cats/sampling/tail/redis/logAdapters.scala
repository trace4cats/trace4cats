package io.janstenpickle.trace4cats.sampling.tail.redis

import dev.profunktor.redis4cats.effect.Log
import org.typelevel.log4cats.Logger

object logAdapters {
  implicit def fromLog4Cats[F[_]](implicit F: Logger[F]): Log[F] =
    new Log[F] {
      def debug(msg: => String): F[Unit] = F.debug(msg)
      def error(msg: => String): F[Unit] = F.error(msg)
      def info(msg: => String): F[Unit] = F.info(msg)
    }
}
