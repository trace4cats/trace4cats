package io.janstenpickle.trace4cats.cats.effect

import cats.{Applicative, Monad}
import cats.data.NonEmptyList
import cats.effect.kernel.{Clock, Ref, Resource}
import cats.syntax.all._
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanSampler}
import io.janstenpickle.trace4cats.model._
import io.janstenpickle.trace4cats.{ErrorHandler, Span}

case class DischargeSpan[F[_]: Monad: Clock: Ref.Make: TraceId.Gen: SpanId.Gen](
  context: SpanContext,
  sampler: SpanSampler[F],
  completer: SpanCompleter[F]
) extends Span[F] {
  def put(key: String, value: AttributeValue): F[Unit] = Applicative[F].unit
  def putAll(fields: (String, AttributeValue)*): F[Unit] = Applicative[F].unit
  def putAll(fields: Map[String, AttributeValue]): F[Unit] = Applicative[F].unit
  def setStatus(spanStatus: SpanStatus): F[Unit] = Applicative[F].unit
  def addLink(link: Link): F[Unit] = Applicative[F].unit
  def addLinks(links: NonEmptyList[Link]): F[Unit] = Applicative[F].unit
  def child(name: String, kind: SpanKind): Resource[F, Span[F]] =
    Resource.eval(SpanContext.root[F]).flatMap(Span.child(name, _, kind, sampler, completer))
  def child(name: String, kind: SpanKind, errorHandler: ErrorHandler): Resource[F, Span[F]] =
    Resource.eval(SpanContext.root[F]).flatMap(Span.child(name, _, kind, sampler, completer, errorHandler))
}

object DischargeSpan {
  def apply[F[_]: Monad: Clock: Ref.Make: TraceId.Gen: SpanId.Gen](
    sampler: SpanSampler[F],
    completer: SpanCompleter[F]
  ): F[DischargeSpan[F]] =
    SpanContext.root[F].map(new DischargeSpan(_, sampler, completer))
}
