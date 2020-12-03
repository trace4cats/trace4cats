package io.janstenpickle.trace4cats.inject

import cats.Applicative
import cats.effect.Resource

trait ContextConstructor[F[_], R, R0] { self =>
  def F: Applicative[F]

  def apply(r: R): Resource[F, R0] = resource(r)

  def resource(r: R): Resource[F, R0]

  def mapBoth[R1](f: (R, R0) => R1): ContextConstructor[F, R, R1] = new ContextConstructor[F, R, R1] {
    override def F: Applicative[F] = self.F

    override def resource(r: R): Resource[F, R1] = self.resource(r).map(f(r, _))(F)
  }

  def mapResult[R1](f: R0 => R1): ContextConstructor[F, R, R1] = mapBoth((_, r0) => f(r0))

  def map[R1](f: R => R1): ContextConstructor[F, R, R1] = mapBoth((r, _) => f(r))

  def contramap[R1](f: R1 => R): ContextConstructor[F, R1, R0] = new ContextConstructor[F, R1, R0] {
    override def F: Applicative[F] = self.F

    override def resource(r: R1): Resource[F, R0] = self.resource(f(r))
  }

  def mapBothF[R1](f: (R, R0) => F[R1]): ContextConstructor[F, R, R1] = new ContextConstructor[F, R, R1] {
    override def F: Applicative[F] = self.F

    override def resource(r: R): Resource[F, R1] = self.resource(r).evalMap(f(r, _))(F)
  }

  def mapBothResource[R1](f: (R, R0) => Resource[F, R1]): ContextConstructor[F, R, R1] =
    new ContextConstructor[F, R, R1] {
      override def F: Applicative[F] = self.F

      override def resource(r: R): Resource[F, R1] = self.resource(r).flatMap(f(r, _))
    }
}

object ContextConstructor {

  def instance[F[_]: Applicative, R, R0](f: R => Resource[F, R0]): ContextConstructor[F, R, R0] =
    new ContextConstructor[F, R, R0] {
      override def F: Applicative[F] = implicitly

      override def resource(r: R): Resource[F, R0] = f(r)
    }
}
