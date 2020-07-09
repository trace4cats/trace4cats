package io.janstenpickle.trace4cats.natchez

import _root_.natchez.{Kernel, Span, TraceValue => V}
import cats.Applicative
import cats.effect.{Clock, Resource, Sync}
import io.janstenpickle.trace4cats.ToHeaders
import io.janstenpickle.trace4cats.model.SpanKind
import io.janstenpickle.trace4cats.model.AttributeValue._

final case class Trace4CatsSpan[F[_]: Sync: Clock](span: io.janstenpickle.trace4cats.Span[F], toHeaders: ToHeaders)
    extends Span[F] {
  override def put(fields: (String, V)*): F[Unit] =
    span.putAll(fields.map {
      case (k, V.StringValue(v)) => k -> StringValue(v)
      case (k, V.NumberValue(v)) => k -> DoubleValue(v.doubleValue())
      case (k, V.BooleanValue(v)) => k -> BooleanValue(v)
    }: _*)

  override def kernel: F[Kernel] = Applicative[F].pure(Kernel(toHeaders.fromContext(span.context)))

  override def span(name: String): Resource[F, Span[F]] =
    Trace4CatsSpan(span.child(name, SpanKind.Internal), toHeaders)
}

object Trace4CatsSpan {
  def apply[F[_]: Sync: Clock](
    span: Resource[F, io.janstenpickle.trace4cats.Span[F]],
    toHeaders: ToHeaders
  ): Resource[F, Span[F]] =
    span.map(Trace4CatsSpan(_, toHeaders))
}
