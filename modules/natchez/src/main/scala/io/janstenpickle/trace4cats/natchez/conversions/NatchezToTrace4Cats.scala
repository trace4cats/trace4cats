package io.janstenpickle.trace4cats.natchez.conversions

import _root_.natchez.{Trace => NatchezTrace, TraceValue => V}
import cats.Applicative
import cats.syntax.foldable._
import cats.syntax.functor._
import io.janstenpickle.trace4cats.inject.Trace
import io.janstenpickle.trace4cats.model.AttributeValue.{
  BooleanList,
  BooleanValue,
  DoubleList,
  DoubleValue,
  LongList,
  LongValue,
  StringList,
  StringValue
}
import io.janstenpickle.trace4cats.model.{AttributeValue, SpanKind, SpanStatus, TraceHeaders}
import io.janstenpickle.trace4cats.natchez.KernelConverter
import io.janstenpickle.trace4cats.{ErrorHandler, ToHeaders}

trait NatchezToTrace4Cats {
  implicit def natchezToTrace4Cats[F[_]: Applicative](implicit trace: NatchezTrace[F]): Trace[F] =
    new Trace[F] {
      override def put(key: String, value: AttributeValue): F[Unit] = putAll(key -> value)
      override def putAll(fields: (String, AttributeValue)*): F[Unit] =
        trace.put(fields.map {
          case (k, StringValue(v)) => k -> V.StringValue(v.value)
          case (k, DoubleValue(v)) => k -> V.NumberValue(v.value)
          case (k, LongValue(v)) => k -> V.NumberValue(v.value)
          case (k, BooleanValue(v)) => k -> V.BooleanValue(v.value)
          case (k, StringList(v)) => k -> V.StringValue(v.value.mkString_("[", ", ", "]"))
          case (k, BooleanList(v)) => k -> V.StringValue(v.value.mkString_("[", ", ", "]"))
          case (k, DoubleList(v)) => k -> V.StringValue(v.value.mkString_("[", ", ", "]"))
          case (k, LongList(v)) => k -> V.StringValue(v.value.mkString_("[", ", ", "]"))
        }: _*)
      override def span[A](name: String, kind: SpanKind, errorHandler: ErrorHandler)(fa: F[A]): F[A] =
        trace.span(name)(fa)
      override def headers(toHeaders: ToHeaders): F[TraceHeaders] = trace.kernel.map(KernelConverter.from)
      override def setStatus(status: SpanStatus): F[Unit] = Applicative[F].unit
      override def traceId: F[Option[String]] = trace.traceId
    }
}
