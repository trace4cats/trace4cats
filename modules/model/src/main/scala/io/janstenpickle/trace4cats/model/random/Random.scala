package io.janstenpickle.trace4cats.model.random

import cats.effect.kernel.Sync

import java.util.concurrent.ThreadLocalRandom

trait Random[F[_]] {
  def nextBytes(n: Int): F[Array[Byte]]
}

object Random {
  def apply[F[_]](implicit ev: Random[F]): Random[F] = ev

  implicit def threadLocal[F[_]: Sync]: Random[F] = n =>
    Sync[F].delay {
      val array = Array.fill[Byte](n)(0)
      ThreadLocalRandom.current.nextBytes(array)
      array
    }
}
