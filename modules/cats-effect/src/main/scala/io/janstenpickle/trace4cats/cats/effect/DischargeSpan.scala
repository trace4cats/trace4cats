package io.janstenpickle.trace4cats.cats.effect

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.kernel.{Resource, Sync}
import cats.syntax.all._
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanSampler}
import io.janstenpickle.trace4cats.model._
import io.janstenpickle.trace4cats.{ErrorHandler, Span}

case class DischargeSpan[F[_]: Sync](context: SpanContext, sampler: SpanSampler[F], completer: SpanCompleter[F])
    extends Span[F] {
  override def put(key: String, value: AttributeValue): F[Unit] = Applicative[F].unit
  override def putAll(fields: (String, AttributeValue)*): F[Unit] = Applicative[F].unit
  override def putAll(fields: Map[String, AttributeValue]): F[Unit] = Applicative[F].unit
  override def setStatus(spanStatus: SpanStatus): F[Unit] = Applicative[F].unit
  override def addLink(link: Link): F[Unit] = Applicative[F].unit
  override def addLinks(links: NonEmptyList[Link]): F[Unit] = Applicative[F].unit
  override def child(name: String, kind: SpanKind): Resource[F, Span[F]] =
    Resource.eval(SpanContext.root[F]).flatMap(Span.child(name, _, kind, sampler, completer))
  override def child(name: String, kind: SpanKind, errorHandler: ErrorHandler): Resource[F, Span[F]] =
    Resource.eval(SpanContext.root[F]).flatMap(Span.child(name, _, kind, sampler, completer, errorHandler))
}

object DischargeSpan {
  def apply[F[_]: Sync](sampler: SpanSampler[F], completer: SpanCompleter[F]): F[DischargeSpan[F]] =
    SpanContext.root[F].map(new DischargeSpan(_, sampler, completer))
}
