package io.janstenpickle.trace4cats.fs2

import cats.{Applicative, Defer}
import cats.effect.Resource
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.inject.{LiftTrace, Provide}
import io.janstenpickle.trace4cats.model.{AttributeValue, SpanContext, SpanKind, SpanStatus}

trait ContinuationSpan[F[_]] extends Span[F] {
  def run[A](k: F[A]): F[A]
}

object ContinuationSpan {
  def fromSpan[F[_]: Applicative: Defer, G[_]: Applicative: Defer](
    span: Span[F]
  )(implicit provide: Provide[F, G], liftTrace: LiftTrace[F, G]): ContinuationSpan[G] = {
    // 👀
    val spanK: Span[G] = span.mapK(liftTrace.fk)

    new ContinuationSpan[G] {
      override def context: SpanContext = spanK.context

      override def put(key: String, value: AttributeValue): G[Unit] = spanK.put(key, value)

      override def putAll(fields: (String, AttributeValue)*): G[Unit] = spanK.putAll(fields: _*)

      override def setStatus(spanStatus: SpanStatus): G[Unit] = spanK.setStatus(spanStatus)

      override def child(name: String, kind: SpanKind): Resource[G, Span[G]] = spanK.child(name, kind)

      override def child(
        name: String,
        kind: SpanKind,
        errorHandler: PartialFunction[Throwable, SpanStatus]
      ): Resource[G, Span[G]] = spanK.child(name, kind, errorHandler)

      override def run[A](k: G[A]): G[A] = liftTrace(provide(k)(span))
    }
  }
}
