// Adapted from Natchez

// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package io.janstenpickle.trace4cats.inject

import cats.Applicative
import cats.effect.{BracketThrow, Clock, Resource, Sync}
import io.janstenpickle.trace4cats.base.context.Inject
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanSampler}
import io.janstenpickle.trace4cats.model.{SpanKind, TraceHeaders}
import io.janstenpickle.trace4cats.{Span, ToHeaders}

/** An entry point, for creating root spans or continuing traces that were started on another
  * system.
  */
trait EntryPoint[F[_]] {

  /** Resource that creates a new root span in a new trace. */
  def root(name: String): Resource[F, Span[F]] = root(name, SpanKind.Internal)
  def root(name: String, kind: SpanKind): Resource[F, Span[F]]

  /** Resource that attempts to creates a new child span but falls back to a new root
    * span as with `root` if the headers do not contain the required values. In other words, we
    * continue the existing span if we can, otherwise we start a new one.
    */
  def continueOrElseRoot(name: String, headers: TraceHeaders): Resource[F, Span[F]] =
    continueOrElseRoot(name, SpanKind.Server, headers)
  def continueOrElseRoot(name: String, kind: SpanKind, headers: TraceHeaders): Resource[F, Span[F]]
}

object EntryPoint {
  def apply[F[_]](implicit entryPoint: EntryPoint[F]): EntryPoint[F] = entryPoint

  def apply[F[_]: Sync: Clock](
    sampler: SpanSampler[F],
    completer: SpanCompleter[F],
    toHeaders: ToHeaders = ToHeaders.all
  ): EntryPoint[F] =
    new EntryPoint[F] {
      override def root(name: String, kind: SpanKind): Resource[F, Span[F]] =
        Span.root[F](name, kind, sampler, completer)

      override def continueOrElseRoot(name: String, kind: SpanKind, headers: TraceHeaders): Resource[F, Span[F]] =
        toHeaders.toContext(headers).fold(root(name, kind)) { parent =>
          Span.child[F](name, parent, kind, sampler, completer)
        }
    }

  def noop[F[_]: Applicative]: EntryPoint[F] =
    new EntryPoint[F] {
      override def root(name: String, kind: SpanKind): Resource[F, Span[F]] = Span.noop[F]
      override def continueOrElseRoot(name: String, kind: SpanKind, headers: TraceHeaders): Resource[F, Span[F]] =
        Span.noop[F]
    }

  def injectRoot[F[_]: BracketThrow](ep: EntryPoint[F]): Inject[F, Span[F], String] = inject(ep.root)

  def injectRootKind[F[_]: BracketThrow](ep: EntryPoint[F]): Inject[F, Span[F], (String, SpanKind)] = inject {
    case (name, kind) => ep.root(name, kind)
  }

  def injectContinue[F[_]: BracketThrow](ep: EntryPoint[F]): Inject[F, Span[F], (String, TraceHeaders)] = inject {
    case (name, headers) => ep.continueOrElseRoot(name, headers)
  }

  def injectContinueKind[F[_]: BracketThrow](ep: EntryPoint[F]): Inject[F, Span[F], (String, SpanKind, TraceHeaders)] =
    inject { case (name, kind, headers) => ep.continueOrElseRoot(name, kind, headers) }

  private def inject[F[_]: BracketThrow, R](f: R => Resource[F, Span[F]]): Inject[F, Span[F], R] =
    new Inject[F, Span[F], R] {
      override def apply[A](r0: R)(g: Span[F] => F[A]): F[A] = f(r0).use(g)
    }

}
