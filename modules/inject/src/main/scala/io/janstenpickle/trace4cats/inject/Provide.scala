package io.janstenpickle.trace4cats.inject

import cats.data.Kleisli
import cats.effect.Bracket
import cats.~>
import io.janstenpickle.trace4cats.Span

trait Provide[F[_], G[_]] {
  def apply[A](ga: G[A])(span: Span[F]): F[A] = provide(ga)(span)
  def provide[A](ga: G[A])(span: Span[F]): F[A] = fk(span)(ga)
  def fk(span: Span[F]): G ~> F
  def noop[A](ga: G[A]): F[A] = noopFk(ga)
  def noopFk: G ~> F
}

object Provide {
  def apply[F[_], G[_]](implicit provide: Provide[F, G]): Provide[F, G] = provide

  implicit def kleisliProvide[F[_]: Bracket[*[_], Throwable]]: Provide[F, Kleisli[F, Span[F], *]] =
    new Provide[F, Kleisli[F, Span[F], *]] {
      override def fk(span: Span[F]): Kleisli[F, Span[F], *] ~> F =
        new (Kleisli[F, Span[F], *] ~> F) {
          override def apply[A](fa: Kleisli[F, Span[F], A]): F[A] = fa.run(span)
        }

      override val noopFk: Kleisli[F, Span[F], *] ~> F = new (Kleisli[F, Span[F], *] ~> F) {
        override def apply[A](fa: Kleisli[F, Span[F], A]): F[A] = Span.noop[F].use(fa.run)
      }
    }
}
