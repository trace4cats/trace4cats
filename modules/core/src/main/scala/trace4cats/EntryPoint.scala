// Adapted from Natchez

// Copyright (c) 2019 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package trace4cats

import cats.data.Kleisli
import cats.effect.kernel.{Clock, MonadCancelThrow, Ref, Resource}
import cats.{~>, Applicative, Monad}

/** An entry point, for creating root spans or continuing traces that were started on another system.
  */
trait EntryPoint[F[_]] {

  /** Resource that creates a new root span in a new trace. */
  def root(name: SpanName): Resource[F, Span[F]] = root(name, SpanKind.Internal)
  def root(name: SpanName, kind: SpanKind): Resource[F, Span[F]] = root(name, kind, ErrorHandler.empty)
  def root(name: SpanName, kind: SpanKind, errorHandler: ErrorHandler): Resource[F, Span[F]]

  /** Resource that attempts to creates a new child span but falls back to a new root span as with `root` if the headers
    * do not contain the required values. In other words, we continue the existing span if we can, otherwise we start a
    * new one.
    */
  def continueOrElseRoot(name: SpanName, headers: TraceHeaders): Resource[F, Span[F]] =
    continueOrElseRoot(name, SpanKind.Server, headers)
  def continueOrElseRoot(name: SpanName, kind: SpanKind, headers: TraceHeaders): Resource[F, Span[F]] =
    continueOrElseRoot(name, kind, headers, ErrorHandler.empty)
  def continueOrElseRoot(
    name: SpanName,
    kind: SpanKind,
    headers: TraceHeaders,
    errorHandler: ErrorHandler
  ): Resource[F, Span[F]]

  def toKleisli: ResourceKleisli[F, SpanParams, Span[F]] =
    Kleisli { case (name, kind, headers, errorHandler) =>
      continueOrElseRoot(name, kind, headers, errorHandler)
    }

  final def mapK[G[_]](fk: F ~> G)(implicit F: MonadCancelThrow[F], G: MonadCancelThrow[G]): EntryPoint[G] =
    EntryPoint.mapK(fk)(this)
}

object EntryPoint {
  def apply[F[_]](implicit entryPoint: EntryPoint[F]): EntryPoint[F] = entryPoint

  /** Create a trace entrypoint for starting or continuing a trace
    *
    * @param sampler
    *   [[trace4cats.kernel.SpanSampler]] implementation
    * @param completer
    *   [[trace4cats.kernel.SpanCompleter]] implementation
    * @param toHeaders
    *   [[ToHeaders]] implementation. Converts span context to headers that may be propagated outside of the
    *   application. Defaults to `ToHeaders.standard`, which is a collection of headers that conform to open standards.
    *   Other header implementations that do not conform to open standards are supported. See [[ToHeaders]] for details
    *   or use `ToHeaders.all`
    */
  def apply[F[_]: Monad: Clock: Ref.Make: TraceId.Gen: SpanId.Gen](
    sampler: SpanSampler[F],
    completer: SpanCompleter[F],
    toHeaders: ToHeaders = ToHeaders.standard
  ): EntryPoint[F] =
    new EntryPoint[F] {
      override def root(name: SpanName, kind: SpanKind, errorHandler: ErrorHandler): Resource[F, Span[F]] =
        Span.root[F](name, kind, sampler, completer, errorHandler)

      override def continueOrElseRoot(
        name: SpanName,
        kind: SpanKind,
        headers: TraceHeaders,
        errorHandler: ErrorHandler
      ): Resource[F, Span[F]] =
        toHeaders.toContext(headers).fold(root(name, kind)) { parent =>
          Span.child[F](name, parent, kind, sampler, completer, errorHandler)
        }
    }

  def noop[F[_]: Applicative]: EntryPoint[F] =
    new EntryPoint[F] {
      override def root(name: SpanName, kind: SpanKind, errorHandler: ErrorHandler): Resource[F, Span[F]] = Span.noop[F]
      override def continueOrElseRoot(
        name: SpanName,
        kind: SpanKind,
        headers: TraceHeaders,
        errorHandler: ErrorHandler
      ): Resource[F, Span[F]] =
        Span.noop[F]
    }

  private def mapK[F[_]: MonadCancelThrow, G[_]: MonadCancelThrow](fk: F ~> G)(ep: EntryPoint[F]): EntryPoint[G] =
    new EntryPoint[G] {
      def root(name: SpanName, kind: SpanKind, errorHandler: ErrorHandler): Resource[G, Span[G]] =
        ep.root(name, kind, errorHandler).map(_.mapK(fk)).mapK(fk)
      def continueOrElseRoot(
        name: SpanName,
        kind: SpanKind,
        headers: TraceHeaders,
        errorHandler: ErrorHandler
      ): Resource[G, Span[G]] =
        ep.continueOrElseRoot(name, kind, headers, errorHandler).map(_.mapK(fk)).mapK(fk)
    }
}
