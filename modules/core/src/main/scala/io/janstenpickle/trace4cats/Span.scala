package io.janstenpickle.trace4cats

import java.util.concurrent.TimeUnit

import cats.Applicative
import cats.effect.concurrent.Ref
import cats.effect.{Clock, ExitCase, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanSampler}
import io.janstenpickle.trace4cats.model._

trait Span[F[_]] {
  def context: SpanContext
  def put(key: String, value: TraceValue): F[Unit]
  def putAll(fields: (String, TraceValue)*): F[Unit]
  def setStatus(spanStatus: SpanStatus): F[Unit]
  protected[trace4cats] def end: F[Unit]
  protected[trace4cats] def end(status: SpanStatus): F[Unit]
}

case class RefSpan[F[_]: Sync: Clock] private (
  context: SpanContext,
  name: String,
  kind: SpanKind,
  start: Long,
  attributes: Ref[F, Map[String, TraceValue]],
  status: Ref[F, SpanStatus],
  completer: SpanCompleter[F]
) extends Span[F] {

  override def put(key: String, value: TraceValue): F[Unit] =
    attributes.update(_ + (key -> value))
  override def putAll(fields: (String, TraceValue)*): F[Unit] =
    attributes.update(_ ++ fields)
  override def setStatus(spanStatus: SpanStatus): F[Unit] = status.set(spanStatus)

  override protected[trace4cats] def end: F[Unit] = status.get.flatMap(end)
  override protected[trace4cats] def end(status: SpanStatus): F[Unit] =
    for {
      now <- Clock[F].realTime(TimeUnit.MICROSECONDS)
      attrs <- attributes.get
      completed = CompletedSpan(context, name, kind, start, now, attrs, status)
      _ <- completer.complete(completed)
    } yield ()

}

case class EmptySpan[F[_]: Applicative] private (context: SpanContext) extends Span[F] {
  override def put(key: String, value: TraceValue): F[Unit] = Applicative[F].unit
  override def putAll(fields: (String, TraceValue)*): F[Unit] = Applicative[F].unit
  override def setStatus(spanStatus: SpanStatus): F[Unit] = Applicative[F].unit

  override protected[trace4cats] def end: F[Unit] = Applicative[F].unit
  override protected[trace4cats] def end(status: SpanStatus): F[Unit] = Applicative[F].unit
}

object Span {
  private def makeSpan[F[_]: Sync: Clock](
    name: String,
    parent: Option[SpanContext],
    context: SpanContext,
    kind: SpanKind,
    sampler: SpanSampler[F],
    completer: SpanCompleter[F]
  ): Resource[F, Span[F]] =
    Resource
      .liftF(
        sampler
          .shouldSample(parent, context.traceId, name, kind)
      )
      .ifM(
        Resource.make(Applicative[F].pure(EmptySpan[F](context.setIsSampled())))(_.end),
        Resource.makeCase(for {
          attributesRef <- Ref.of[F, Map[String, TraceValue]](Map.empty)
          now <- Clock[F].realTime(TimeUnit.MICROSECONDS)
          statusRef <- Ref.of[F, SpanStatus](SpanStatus.Ok)
        } yield RefSpan[F](context, name, kind, now, attributesRef, statusRef, completer)) {
          case (span, ExitCase.Completed) => span.end
          case (span, ExitCase.Canceled) => span.end(SpanStatus.Cancelled)
          case (span, ExitCase.Error(th)) =>
            span.putAll("error" -> true, "error.message" -> th.getMessage) >> span.end(SpanStatus.Internal)
        }
      )

  def child[F[_]: Sync: Clock](
    name: String,
    parent: SpanContext,
    kind: SpanKind,
    sampler: SpanSampler[F],
    completer: SpanCompleter[F],
  ): Resource[F, Span[F]] =
    Resource.liftF(SpanContext.child[F](parent)).flatMap(makeSpan(name, Some(parent), _, kind, sampler, completer))

  def root[F[_]: Sync: Clock](
    name: String,
    kind: SpanKind,
    sampler: SpanSampler[F],
    completer: SpanCompleter[F],
  ): Resource[F, Span[F]] =
    Resource.liftF(SpanContext.root[F]).flatMap(makeSpan(name, None, _, kind, sampler, completer))

}
