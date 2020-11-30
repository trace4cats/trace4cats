// Adapted from Natchez
// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package io.janstenpickle.trace4cats.inject

import cats.data.{EitherT, Kleisli}
import cats.effect.Bracket
import cats.syntax.applicative._
import cats.syntax.option._
import cats.syntax.show._
import cats.{Applicative, Defer, Functor}
import io.janstenpickle.trace4cats.base.context.{Lift, Local}
import io.janstenpickle.trace4cats.model.{AttributeValue, SpanKind, SpanStatus, TraceHeaders}
import io.janstenpickle.trace4cats.{Span, ToHeaders}

/** A tracing effect, which always has a current span. */
trait Trace[F[_]] {
  def put(key: String, value: AttributeValue): F[Unit]
  def putAll(fields: (String, AttributeValue)*): F[Unit]
  def span[A](name: String)(fa: F[A]): F[A] = span(name, SpanKind.Internal)(fa)
  def span[A](name: String, kind: SpanKind)(fa: F[A]): F[A]
  def headers: F[TraceHeaders] = headers(ToHeaders.all)
  def headers(toHeaders: ToHeaders): F[TraceHeaders]
  def setStatus(status: SpanStatus): F[Unit]
  def traceId: F[Option[String]]
}

object Trace extends TraceInstancesLowPriority {

  def apply[F[_]](implicit ev: Trace[F]): ev.type = ev

  object Implicits {

    /** A no-op `Trace` implementation is freely available for any applicative effect. This lets us add
      * a `Trace` constraint to most existing code without demanding anything new from the concrete
      * effect type.
      */
    implicit def noop[F[_]: Applicative]: Trace[F] =
      new Trace[F] {
        final val void = ().pure[F]
        override val headers: F[TraceHeaders] = TraceHeaders.empty.pure[F]
        override def headers(toHeaders: ToHeaders): F[TraceHeaders] = TraceHeaders.empty.pure[F]
        override def put(key: String, value: AttributeValue): F[Unit] = void
        override def putAll(fields: (String, AttributeValue)*): F[Unit] = void
        override def span[A](name: String)(fa: F[A]): F[A] = fa
        override def span[A](name: String, kind: SpanKind)(fa: F[A]): F[A] = fa
        override def setStatus(status: SpanStatus): F[Unit] = void
        override def traceId: F[Option[String]] = Option.empty[String].pure[F]
      }

  }

  /** `Kleisli[F, Span[F], *]` is a `Trace` given `Bracket[F, Throwable]`. The instance can be
    * widened to an environment that *contains* a `Span[F]` via the `lens` method.
    */
  implicit def kleisliInstance[F[_]: Bracket[*[_], Throwable]]: KleisliTrace[F] =
    new KleisliTrace[F]

  /** A trace instance for `Kleisli[F, Span[F], *]`, which is the mechanism we use to introduce
    * context into our computations. We can also "lensMap" out to `Kleisli[F, E, *]` given a lens
    * from `E` to `Span[F]`.
    */
  class KleisliTrace[F[_]: Bracket[*[_], Throwable]] extends Trace[Kleisli[F, Span[F], *]] {

    override def headers(toHeaders: ToHeaders): Kleisli[F, Span[F], TraceHeaders] =
      Kleisli { span =>
        toHeaders.fromContext(span.context).pure[F]
      }

    override def put(key: String, value: AttributeValue): Kleisli[F, Span[F], Unit] =
      Kleisli(_.put(key, value))

    override def putAll(fields: (String, AttributeValue)*): Kleisli[F, Span[F], Unit] =
      Kleisli(_.putAll(fields: _*))

    override def span[A](name: String, kind: SpanKind)(k: Kleisli[F, Span[F], A]): Kleisli[F, Span[F], A] =
      Kleisli(_.child(name, kind).use(k.run))

    override def setStatus(status: SpanStatus): Kleisli[F, Span[F], Unit] = Kleisli(_.setStatus(status))

    override def traceId: Kleisli[F, Span[F], Option[String]] = Kleisli(span => span.context.traceId.show.some.pure[F])

    def lens[E](f: E => Span[F], g: (E, Span[F]) => E): Trace[Kleisli[F, E, *]] =
      new Trace[Kleisli[F, E, *]] {
        override def put(key: String, value: AttributeValue): Kleisli[F, E, Unit] =
          Kleisli(e => f(e).put(key, value))

        override def putAll(fields: (String, AttributeValue)*): Kleisli[F, E, Unit] =
          Kleisli(e => f(e).putAll(fields: _*))

        override def span[A](name: String, kind: SpanKind)(k: Kleisli[F, E, A]): Kleisli[F, E, A] =
          Kleisli(e => f(e).child(name, kind).use(s => k.run(g(e, s))))

        override def headers(toHeaders: ToHeaders): Kleisli[F, E, TraceHeaders] =
          Kleisli(e => toHeaders.fromContext(f(e).context).pure[F])

        override def setStatus(status: SpanStatus): Kleisli[F, E, Unit] =
          Kleisli(e => f(e).setStatus(status))

        override def traceId: Kleisli[F, E, Option[String]] = Kleisli(e => f(e).context.traceId.show.some.pure[F])
      }

  }

  implicit def eitherTTrace[F[_]: Functor, A](implicit trace: Trace[F]): Trace[EitherT[F, A, *]] =
    new Trace[EitherT[F, A, *]] {
      override def put(key: String, value: AttributeValue): EitherT[F, A, Unit] = EitherT.liftF(trace.put(key, value))

      override def putAll(fields: (String, AttributeValue)*): EitherT[F, A, Unit] =
        EitherT.liftF(trace.putAll(fields: _*))

      override def span[B](name: String, kind: SpanKind)(fa: EitherT[F, A, B]): EitherT[F, A, B] =
        EitherT(trace.span(name, kind)(fa.value))

      override def headers(toHeaders: ToHeaders): EitherT[F, A, TraceHeaders] =
        EitherT.liftF(trace.headers(toHeaders))

      override def setStatus(status: SpanStatus): EitherT[F, A, Unit] =
        EitherT.liftF(trace.setStatus(status))

      override def traceId: EitherT[F, A, Option[String]] = EitherT.liftF(trace.traceId)
    }
}

trait TraceInstancesLowPriority {
  implicit def localSpanInstance[F[_], G[_]](implicit
    C: Local[G, Span[F]],
    L: Lift[F, G],
    G1: Bracket[G, Throwable],
    G2: Defer[G]
  ): Trace[G] = new Trace[G] {
    def put(key: String, value: AttributeValue): G[Unit] = C.accessF(span => L.lift(span.put(key, value)))
    def putAll(fields: (String, AttributeValue)*): G[Unit] = C.accessF(span => L.lift(span.putAll(fields: _*)))
    def span[A](name: String, kind: SpanKind)(fa: G[A]): G[A] =
      C.accessF(_.child(name, kind).mapK(L.liftK).use(C.scope(fa)))
    def headers(toHeaders: ToHeaders): G[TraceHeaders] =
      C.access(span => toHeaders.fromContext(span.context))
    def setStatus(status: SpanStatus): G[Unit] = C.accessF(span => L.lift(span.setStatus(status)))
    def traceId: G[Option[String]] = C.access(_.context.traceId.show.some)
  }
}
