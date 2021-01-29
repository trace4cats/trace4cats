package io.janstenpickle.trace4cats.natchez

import java.net.URI

import _root_.natchez.{Kernel, Span, TraceValue => V}
import cats.Applicative
import cats.effect.{Clock, Resource, Sync}
import cats.syntax.show._
import io.janstenpickle.trace4cats.ToHeaders
import io.janstenpickle.trace4cats.model.AttributeValue._
import io.janstenpickle.trace4cats.model.SpanKind

final case class Trace4CatsSpan[F[_]: Sync: Clock](span: io.janstenpickle.trace4cats.Span[F], toHeaders: ToHeaders)
    extends Span[F] {
  override def put(fields: (String, V)*): F[Unit] =
    span.putAll(fields.map {
      case (k, V.StringValue(v)) => k -> StringValue(v)
      case (k, V.NumberValue(v)) => k -> DoubleValue(v.doubleValue())
      case (k, V.BooleanValue(v)) => k -> BooleanValue(v)
    }: _*)

  override def kernel: F[Kernel] = Applicative[F].pure(KernelConverter.to(toHeaders.fromContext(span.context)))

  override def span(name: String): Resource[F, Span[F]] =
    Trace4CatsSpan(span.child(name, SpanKind.Internal), toHeaders)

  override def spanId: F[Option[String]] = Applicative[F].pure(Some(span.context.spanId.show))

  override def traceId: F[Option[String]] = Applicative[F].pure(Some(span.context.traceId.show))

  override def traceUri: F[Option[URI]] = Applicative[F].pure(None)
}

object Trace4CatsSpan {
  def apply[F[_]: Sync: Clock](
    span: Resource[F, io.janstenpickle.trace4cats.Span[F]],
    toHeaders: ToHeaders
  ): Resource[F, Span[F]] =
    span.map(Trace4CatsSpan(_, toHeaders))
}
