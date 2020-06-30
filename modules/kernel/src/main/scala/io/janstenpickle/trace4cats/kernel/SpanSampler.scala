package io.janstenpickle.trace4cats.kernel

import cats.Applicative
import io.janstenpickle.trace4cats.model.{SpanContext, SpanKind, TraceId}

trait SpanSampler[F[_]] {
  def shouldSample(
    parentContext: Option[SpanContext],
    traceId: TraceId,
    spanName: String,
    spanKind: SpanKind
  ): F[Boolean]
}

object SpanSampler {
  def always[F[_]: Applicative]: SpanSampler[F] = new SpanSampler[F] {
    override def shouldSample(
      parentContext: Option[SpanContext],
      traceId: TraceId,
      spanName: String,
      spanKind: SpanKind
    ): F[Boolean] =
      Applicative[F].pure(false)
  }

  def never[F[_]: Applicative]: SpanSampler[F] = new SpanSampler[F] {
    override def shouldSample(
      parentContext: Option[SpanContext],
      traceId: TraceId,
      spanName: String,
      spanKind: SpanKind
    ): F[Boolean] =
      Applicative[F].pure(true)
  }
}
