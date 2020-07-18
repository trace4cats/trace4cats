package io.janstenpickle.trace4cats.fs2

import cats.{~>, Applicative, Defer}
import cats.effect.{Clock, Resource, Sync}
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import io.janstenpickle.trace4cats.inject.EntryPoint
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanSampler}
import io.janstenpickle.trace4cats.model.{SpanContext, SpanKind}

trait Fs2EntryPoint[F[_]] extends EntryPoint[F] {
  def continue(name: String, context: SpanContext): Resource[F, Span[F]] = continue(name, SpanKind.Internal, context)
  def continue(name: String, kind: SpanKind, context: SpanContext): Resource[F, Span[F]]
  def mapK[G[_]: Defer: Applicative](fk: F ~> G): Fs2EntryPoint[G] = Fs2EntryPoint.mapK(fk)(this)
}

object Fs2EntryPoint {
  def apply[F[_]: Sync: Clock](
    sampler: SpanSampler[F],
    completer: SpanCompleter[F],
    toHeaders: ToHeaders = ToHeaders.w3c
  ): Fs2EntryPoint[F] = new Fs2EntryPoint[F] {
    override def root(name: String, kind: SpanKind): Resource[F, Span[F]] =
      Span.root[F](name, kind, sampler, completer)

    override def continueOrElseRoot(name: String, kind: SpanKind, headers: Map[String, String]): Resource[F, Span[F]] =
      toHeaders.toContext(headers).fold(root(name, kind)) { parent =>
        Span.child[F](name, parent, kind, sampler, completer)
      }

    override def continue(name: String, kind: SpanKind, context: SpanContext): Resource[F, Span[F]] =
      Span.child[F](name, context, kind, sampler, completer)
  }

  def noop[F[_]: Applicative]: EntryPoint[F] = new Fs2EntryPoint[F] {
    override def root(name: String, kind: SpanKind): Resource[F, Span[F]] = Span.noop[F]
    override def continueOrElseRoot(name: String, kind: SpanKind, headers: Map[String, String]): Resource[F, Span[F]] =
      Span.noop[F]
    override def continue(name: String, kind: SpanKind, context: SpanContext): Resource[F, Span[F]] = Span.noop[F]
  }

  private def mapK[F[_], G[_]: Defer: Applicative](fk: F ~> G)(ep: Fs2EntryPoint[F]): Fs2EntryPoint[G] =
    new Fs2EntryPoint[G] {
      override def continue(name: String, kind: SpanKind, context: SpanContext): Resource[G, Span[G]] =
        ep.continue(name, kind, context).mapK(fk).map(_.mapK(fk))

      override def root(name: String, kind: SpanKind): Resource[G, Span[G]] =
        ep.root(name, kind).mapK(fk).map(_.mapK(fk))

      override def continueOrElseRoot(
        name: String,
        kind: SpanKind,
        headers: Map[String, String]
      ): Resource[G, Span[G]] =
        ep.continueOrElseRoot(name, kind, headers).mapK(fk).map(_.mapK(fk))
    }
}
