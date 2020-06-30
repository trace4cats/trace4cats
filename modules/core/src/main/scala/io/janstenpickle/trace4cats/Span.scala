package io.janstenpickle.trace4cats

import java.util.concurrent.TimeUnit

import cats.effect.concurrent.Ref
import cats.effect.{Clock, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, Monad}
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanSampler}
import io.janstenpickle.trace4cats.model._

trait Span[F[_]] {
  def context: SpanContext
  def put(key: String, value: TraceValue): F[Unit]
  def putAll(fields: (String, TraceValue)*): F[Unit]
  def end(status: SpanStatus): F[Unit]
}

case class RefSpan[F[_]: Sync: Clock] private (
  context: SpanContext,
  name: String,
  kind: SpanKind,
  start: Long,
  attributes: Ref[F, Map[String, TraceValue]],
  completer: SpanCompleter[F]
) extends Span[F] {

  def put(key: String, value: TraceValue): F[Unit] =
    attributes.update(_ + (key -> value))
  def putAll(fields: (String, TraceValue)*): F[Unit] =
    attributes.update(_ ++ fields)

  def end(status: SpanStatus): F[Unit] =
    for {
      now <- Clock[F].realTime(TimeUnit.MICROSECONDS)
      attrs <- attributes.get
      completed = CompletedSpan(context, name, kind, start, now, attrs, status)
      _ <- completer.complete(completed)
    } yield ()

}

case class EmptySpan[F[_]: Monad] private (context: SpanContext) extends Span[F] {
  override def put(key: String, value: TraceValue): F[Unit] =
    Applicative[F].unit
  override def putAll(fields: (String, TraceValue)*): F[Unit] =
    Applicative[F].unit
  override def end(status: SpanStatus): F[Unit] = Applicative[F].unit
}

object Span {

  def child[F[_]: Sync: Clock](
    name: String,
    parent: SpanContext,
    kind: SpanKind,
    completer: SpanCompleter[F],
  ): F[Span[F]] =
    SpanContext.child[F](parent).flatMap { context =>
      if (parent.traceFlags.sampled)
        Applicative[F].pure(EmptySpan[F](context))
      else
        for {
          context <- SpanContext.child[F](parent)
          attributesRef <- Ref.of[F, Map[String, TraceValue]](Map.empty)
          now <- Clock[F].realTime(TimeUnit.MICROSECONDS)
        } yield RefSpan[F](context, name, kind, now, attributesRef, completer)
    }

  def root[F[_]: Sync: Clock](
    name: String,
    kind: SpanKind,
    sampler: SpanSampler[F],
    completer: SpanCompleter[F],
  ): F[Span[F]] =
    SpanContext.root[F].flatMap { context =>
      sampler
        .shouldSample(None, context.traceId, name, kind)
        .ifM(
          Applicative[F].pure(EmptySpan[F](context.setIsSampled())),
          for {
            context <- SpanContext.root[F]
            attributesRef <- Ref.of[F, Map[String, TraceValue]](Map.empty)
            now <- Clock[F].realTime(TimeUnit.MICROSECONDS)
          } yield RefSpan[F](context, name, kind, now, attributesRef, completer)
        )
    }

}
