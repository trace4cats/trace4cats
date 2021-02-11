// Adapted from Natchez

// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package io.janstenpickle.trace4cats.inject

import cats.Applicative
import cats.data.Kleisli
import cats.effect.{Clock, Resource, Sync}
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanSampler}
import io.janstenpickle.trace4cats.model.{SpanKind, SpanStatus, TraceHeaders}
import io.janstenpickle.trace4cats.{Span, ToHeaders}

/** An entry point, for creating root spans or continuing traces that were started on another
  * system.
  */
trait EntryPoint[F[_]] {

  /** Resource that creates a new root span in a new trace. */
  def root(name: SpanName): Resource[F, Span[F]] = root(name, SpanKind.Internal)
  def root(name: SpanName, kind: SpanKind): Resource[F, Span[F]] = root(name, kind, PartialFunction.empty)
  def root(name: SpanName, kind: SpanKind, errorHandler: PartialFunction[Throwable, SpanStatus]): Resource[F, Span[F]]

  /** Resource that attempts to creates a new child span but falls back to a new root
    * span as with `root` if the headers do not contain the required values. In other words, we
    * continue the existing span if we can, otherwise we start a new one.
    */
  def continueOrElseRoot(name: SpanName, headers: TraceHeaders): Resource[F, Span[F]] =
    continueOrElseRoot(name, SpanKind.Server, headers)
  def continueOrElseRoot(name: SpanName, kind: SpanKind, headers: TraceHeaders): Resource[F, Span[F]] =
    continueOrElseRoot(name, kind, headers, PartialFunction.empty)
  def continueOrElseRoot(
    name: SpanName,
    kind: SpanKind,
    headers: TraceHeaders,
    errorHandler: PartialFunction[Throwable, SpanStatus]
  ): Resource[F, Span[F]]

  def toKleisli: ResourceKleisli[F, SpanParams, Span[F]] =
    Kleisli { case (name, kind, headers, errorHandler) =>
      continueOrElseRoot(name, kind, headers, errorHandler)
    }
}

object EntryPoint {
  def apply[F[_]](implicit entryPoint: EntryPoint[F]): EntryPoint[F] = entryPoint

  def apply[F[_]: Sync: Clock](
    sampler: SpanSampler[F],
    completer: SpanCompleter[F],
    toHeaders: ToHeaders = ToHeaders.all
  ): EntryPoint[F] =
    new EntryPoint[F] {
      override def root(
        name: SpanName,
        kind: SpanKind,
        errorHandler: PartialFunction[Throwable, SpanStatus]
      ): Resource[F, Span[F]] =
        Span.root[F](name, kind, sampler, completer, errorHandler)

      override def continueOrElseRoot(
        name: SpanName,
        kind: SpanKind,
        headers: TraceHeaders,
        errorHandler: PartialFunction[Throwable, SpanStatus]
      ): Resource[F, Span[F]] =
        toHeaders.toContext(headers).fold(root(name, kind)) { parent =>
          Span.child[F](name, parent, kind, sampler, completer, errorHandler)
        }
    }

  def noop[F[_]: Applicative]: EntryPoint[F] =
    new EntryPoint[F] {
      override def root(
        name: SpanName,
        kind: SpanKind,
        errorHandler: PartialFunction[Throwable, SpanStatus]
      ): Resource[F, Span[F]] = Span.noop[F]
      override def continueOrElseRoot(
        name: SpanName,
        kind: SpanKind,
        headers: TraceHeaders,
        errorHandler: PartialFunction[Throwable, SpanStatus]
      ): Resource[F, Span[F]] =
        Span.noop[F]
    }

}
