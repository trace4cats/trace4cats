package io.janstenpickle.trace4cats.model.random

import cats.{ApplicativeError, Defer}

import java.util.concurrent.ThreadLocalRandom

// TODO: CE3 replace with built-in cats.effect.std.Random
trait Random[F[_]] {
  def nextBytes(n: Int): F[Array[Byte]]
}

object Random {
  def apply[F[_]](implicit random: Random[F]): Random[F] = random

  implicit def threadLocal[F[_]: Defer: ApplicativeError[*[_], Throwable]]: Random[F] = new Random[F] {
    override def nextBytes(n: Int): F[Array[Byte]] = Defer[F].defer(ApplicativeError[F, Throwable].catchNonFatal {
      val array: Array[Byte] = Array.fill(n)(0)
      ThreadLocalRandom.current.nextBytes(array)
      array
    })
  }
}
