package io.janstenpickle.trace4cats

import java.time.Instant
import java.util.concurrent.TimeUnit

import cats.{Applicative, Defer, MonadError}
import cats.effect.concurrent.Ref
import cats.effect.{Clock, ExitCase, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanSampler}
import io.janstenpickle.trace4cats.model._

trait Span[F[_]] {
  def context: SpanContext
  def put(key: String, value: AttributeValue): F[Unit]
  def putAll(fields: (String, AttributeValue)*): F[Unit]
  def setStatus(spanStatus: SpanStatus): F[Unit]
  def child(name: String, kind: SpanKind): Resource[F, Span[F]]
  protected[trace4cats] def end: F[Unit]
  protected[trace4cats] def end(status: SpanStatus): F[Unit]
}

case class RefSpan[F[_]: Sync: Clock] private (
  context: SpanContext,
  name: String,
  kind: SpanKind,
  start: Long,
  attributes: Ref[F, Map[String, AttributeValue]],
  status: Ref[F, SpanStatus],
  sampler: SpanSampler[F],
  completer: SpanCompleter[F]
) extends Span[F] {

  override def put(key: String, value: AttributeValue): F[Unit] =
    attributes.update(_ + (key -> value))
  override def putAll(fields: (String, AttributeValue)*): F[Unit] =
    attributes.update(_ ++ fields)
  override def setStatus(spanStatus: SpanStatus): F[Unit] = status.set(spanStatus)
  override def child(name: String, kind: SpanKind): Resource[F, Span[F]] =
    Span.child[F](name, context, kind, sampler, completer)

  override protected[trace4cats] def end: F[Unit] = status.get.flatMap(end)
  override protected[trace4cats] def end(status: SpanStatus): F[Unit] =
    for {
      now <- Clock[F].realTime(TimeUnit.MILLISECONDS)
      attrs <- attributes.get
      completed = CompletedSpan(
        context,
        name,
        kind,
        Instant.ofEpochMilli(start),
        Instant.ofEpochMilli(now),
        attrs,
        status
      )
      _ <- completer.complete(completed)
    } yield ()

}

case class EmptySpan[F[_]: Defer: MonadError[*[_], Throwable]] private (context: SpanContext) extends Span[F] {
  override def put(key: String, value: AttributeValue): F[Unit] = Applicative[F].unit
  override def putAll(fields: (String, AttributeValue)*): F[Unit] = Applicative[F].unit
  override def setStatus(spanStatus: SpanStatus): F[Unit] = Applicative[F].unit
  override def child(name: String, kind: SpanKind): Resource[F, Span[F]] =
    Resource.make(SpanContext.child[F](context).map { childContext =>
      EmptySpan(childContext.setIsSampled())
    })(_.end)

  override protected[trace4cats] def end: F[Unit] = Applicative[F].unit
  override protected[trace4cats] def end(status: SpanStatus): F[Unit] = Applicative[F].unit
}

case class NoopSpan[F[_]: Applicative] private (context: SpanContext) extends Span[F] {
  override def put(key: String, value: AttributeValue): F[Unit] = Applicative[F].unit
  override def putAll(fields: (String, AttributeValue)*): F[Unit] = Applicative[F].unit
  override def setStatus(spanStatus: SpanStatus): F[Unit] = Applicative[F].unit
  override def child(name: String, kind: SpanKind): Resource[F, Span[F]] = Span.noop[F]
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
          attributesRef <- Ref.of[F, Map[String, AttributeValue]](Map.empty)
          now <- Clock[F].realTime(TimeUnit.MILLISECONDS)
          statusRef <- Ref.of[F, SpanStatus](SpanStatus.Ok)
        } yield RefSpan[F](context, name, kind, now, attributesRef, statusRef, sampler, completer)) {
          case (span, ExitCase.Completed) => span.end
          case (span, ExitCase.Canceled) => span.end(SpanStatus.Cancelled)
          case (span, ExitCase.Error(th)) => span.end(SpanStatus.Internal(th.getMessage))
        }
      )

  def noop[F[_]: Applicative]: Resource[F, Span[F]] = Resource.pure[F, Span[F]](NoopSpan[F](SpanContext.invalid))

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
