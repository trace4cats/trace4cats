// Adapted from Natchez
// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package io.janstenpickle.trace4cats.inject

import cats.Applicative
import cats.data.Kleisli
import cats.effect.Bracket
import io.janstenpickle.trace4cats.model.{AttributeValue, SpanKind, SpanStatus}
import cats.syntax.applicative._
import io.janstenpickle.trace4cats.{Span, ToHeaders}

/** A tracing effect, which always has a current span. */
trait Trace[F[_]] {
  def put(key: String, value: AttributeValue): F[Unit]
  def putAll(fields: (String, AttributeValue)*): F[Unit]
  def span[A](name: String)(fa: F[A]): F[A] = span(name, SpanKind.Internal)(fa)
  def span[A](name: String, kind: SpanKind)(fa: F[A]): F[A]
  def headers: F[Map[String, String]] = headers(ToHeaders.w3c)
  def headers(toHeaders: ToHeaders): F[Map[String, String]]
  def setStatus(status: SpanStatus): F[Unit]
}

object Trace {

  def apply[F[_]](implicit ev: Trace[F]): ev.type = ev

  object Implicits {

    /**
      * A no-op `Trace` implementation is freely available for any applicative effect. This lets us add
      * a `Trace` constraint to most existing code without demanding anything new from the concrete
      * effect type.
      */
    implicit def noop[F[_]: Applicative]: Trace[F] =
      new Trace[F] {
        final val void = ().pure[F]
        override val headers: F[Map[String, String]] = Map.empty[String, String].pure[F]
        override def headers(toHeaders: ToHeaders): F[Map[String, String]] = Map.empty[String, String].pure[F]
        override def put(key: String, value: AttributeValue): F[Unit] = void
        override def putAll(fields: (String, AttributeValue)*): F[Unit] = void
        override def span[A](name: String)(fa: F[A]): F[A] = fa
        override def span[A](name: String, kind: SpanKind)(fa: F[A]): F[A] = fa
        override def setStatus(status: SpanStatus): F[Unit] = void
      }

  }

  /**
    * `Kleisli[F, Span[F], *]` is a `Trace` given `Bracket[F, Throwable]`. The instance can be
    * widened to an environment that *contains* a `Span[F]` via the `lens` method.
    */
  implicit def kleisliInstance[F[_]: Bracket[*[_], Throwable]]: KleisliTrace[F] =
    new KleisliTrace[F]

  /**
    * A trace instance for `Kleisli[F, Span[F], *]`, which is the mechanism we use to introduce
    * context into our computations. We can also "lensMap" out to `Kleisli[F, E, *]` given a lens
    * from `E` to `Span[F]`.
    */
  class KleisliTrace[F[_]: Bracket[*[_], Throwable]] extends Trace[Kleisli[F, Span[F], *]] {

    override def headers(toHeaders: ToHeaders): Kleisli[F, Span[F], Map[String, String]] = Kleisli { span =>
      toHeaders.fromContext(span.context).pure[F]
    }

    override def put(key: String, value: AttributeValue): Kleisli[F, Span[F], Unit] =
      Kleisli(_.put(key, value))

    override def putAll(fields: (String, AttributeValue)*): Kleisli[F, Span[F], Unit] =
      Kleisli(_.putAll(fields: _*))

    override def span[A](name: String, kind: SpanKind)(k: Kleisli[F, Span[F], A]): Kleisli[F, Span[F], A] =
      Kleisli(_.child(name, kind).use(k.run))

    override def setStatus(status: SpanStatus): Kleisli[F, Span[F], Unit] = Kleisli(_.setStatus(status))

    def lens[E](f: E => Span[F], g: (E, Span[F]) => E): Trace[Kleisli[F, E, *]] =
      new Trace[Kleisli[F, E, *]] {
        override def put(key: String, value: AttributeValue): Kleisli[F, E, Unit] =
          Kleisli(e => f(e).put(key, value))

        override def putAll(fields: (String, AttributeValue)*): Kleisli[F, E, Unit] =
          Kleisli(e => f(e).putAll(fields: _*))

        override def span[A](name: String, kind: SpanKind)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
          Kleisli(e => f(e).child(name, kind).use(s => k.run(g(e, s))))

        override def headers(toHeaders: ToHeaders): Kleisli[F, E, Map[String, String]] =
          Kleisli(e => toHeaders.fromContext(f(e).context).pure[F])

        override def setStatus(status: SpanStatus): Kleisli[F, E, Unit] =
          Kleisli(e => f(e).setStatus(status))
      }

  }

}
