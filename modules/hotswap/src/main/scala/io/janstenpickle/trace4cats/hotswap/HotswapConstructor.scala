package io.janstenpickle.trace4cats.hotswap

import cats.Applicative
import cats.effect.kernel.{Resource, Temporal}
import cats.kernel.Eq
import cats.syntax.applicative._
import cats.syntax.eq._
import cats.syntax.functor._
import cats.syntax.flatMap._

trait HotswapConstructor[F[_], P, R] {
  def swap(params: P): F[Boolean]
  def get: Resource[F, (P, R)]
  def currentParams: F[P]
  def resource: Resource[F, R]
}

object HotswapConstructor {
  def apply[F[_]: Temporal, P: Eq, R](
    initialParams: P
  )(make: P => Resource[F, R]): Resource[F, HotswapConstructor[F, P, R]] = {
    val makeImpl: P => Resource[F, (P, R)] = a => make(a).map(a -> _)

    HotswapRef[F, (P, R)](makeImpl(initialParams)).map { hotswap =>
      new HotswapConstructor[F, P, R] {
        override def swap(params: P): F[Boolean] =
          currentParams.map(_.neqv(params)).flatTap(Applicative[F].whenA(_)(hotswap.swap(makeImpl(params))))

        override def get: Resource[F, (P, R)] = hotswap.get
        override def currentParams: F[P] = get.use(_._1.pure)
        override def resource: Resource[F, R] = get.map(_._2)
      }
    }
  }
}
