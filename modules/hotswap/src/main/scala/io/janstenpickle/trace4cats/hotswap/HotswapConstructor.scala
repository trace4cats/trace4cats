package io.janstenpickle.trace4cats.hotswap

import cats.Applicative
import cats.effect.kernel.{Resource, Temporal}
import cats.kernel.Eq
import cats.syntax.applicative._
import cats.syntax.eq._
import cats.syntax.functor._
import cats.syntax.flatMap._

trait HotswapConstructor[F[_], A, B] {
  def swap(a: A): F[Boolean]
  def get: Resource[F, (A, B)]
  def getA: F[A]
  def getB: Resource[F, B]
}

object HotswapConstructor {
  def apply[F[_]: Temporal, A: Eq, B](
    initial: A,
    make: A => Resource[F, B],
  ): Resource[F, HotswapConstructor[F, A, B]] = {
    val makeImpl: A => Resource[F, (A, B)] = a => make(a).map(a -> _)

    HotswapRef[F, (A, B)](makeImpl(initial)).map { hotswap =>
      new HotswapConstructor[F, A, B] {
        override def swap(a: A): F[Boolean] =
          getA.map(_.neqv(a)).flatTap(Applicative[F].whenA(_)(hotswap.swap(makeImpl(a))))

        override def get: Resource[F, (A, B)] = hotswap.get
        override def getA: F[A] = get.use(_._1.pure)
        override def getB: Resource[F, B] = get.map(_._2)
      }
    }
  }
}
