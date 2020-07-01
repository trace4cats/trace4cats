package io.janstenpickle.trace4cats.natchez

import _root_.natchez.{Kernel, Span, TraceValue => V}
import cats.Applicative
import cats.effect.{Clock, ExitCase, Resource, Sync}
import cats.syntax.flatMap._
import io.janstenpickle.trace4cats.ToHeaders
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.TraceValue._
import io.janstenpickle.trace4cats.model.{SpanKind, SpanStatus}

final case class Trace4CatsSpan[F[_]: Sync: Clock](
  span: io.janstenpickle.trace4cats.Span[F],
  completer: SpanCompleter[F],
  toHeaders: ToHeaders
) extends Span[F] {
  override def put(fields: (String, V)*): F[Unit] =
    span.putAll(fields.map {
      case (k, V.StringValue(v)) => k -> StringValue(v)
      case (k, V.NumberValue(v)) => k -> DoubleValue(v.doubleValue())
      case (k, V.BooleanValue(v)) => k -> BooleanValue(v)
    }: _*)

  override def kernel: F[Kernel] = Applicative[F].pure(Kernel(toHeaders.fromContext(span.context)))

  override def span(name: String): Resource[F, Span[F]] =
    Trace4CatsSpan
      .resource(
        io.janstenpickle.trace4cats.Span.child(name, span.context, SpanKind.Internal, completer),
        completer,
        toHeaders
      )
}

object Trace4CatsSpan {
  def resource[F[_]: Sync: Clock](
    span: F[io.janstenpickle.trace4cats.Span[F]],
    completer: SpanCompleter[F],
    toHeaders: ToHeaders
  ): Resource[F, Span[F]] =
    Resource
      .makeCase(span) {
        case (span, ExitCase.Completed) => span.end(SpanStatus.Ok)
        case (span, ExitCase.Canceled) => span.end(SpanStatus.Cancelled)
        case (span, ExitCase.Error(th)) =>
          span.putAll("error" -> true, "error.message" -> th.getMessage) >> span.end(SpanStatus.Internal)
      }
      .map(Trace4CatsSpan(_, completer, toHeaders))
}
