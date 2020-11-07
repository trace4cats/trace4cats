package io.janstenpickle.trace4cats.natchez.conversions

import java.net.URI

import _root_.natchez.{Kernel, Trace => NatchezTrace, TraceValue => V}
import cats.Applicative
import cats.syntax.functor._
import io.janstenpickle.trace4cats.ToHeaders
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
import io.janstenpickle.trace4cats.model.{AttributeValue, SpanKind, SpanStatus}

trait Trace4CatsConversions {
  implicit def trace4CatsToNatchez[F[_]: Applicative](implicit trace: Trace[F]): NatchezTrace[F] =
    new NatchezTrace[F] {
      override def put(fields: (String, V)*): F[Unit] =
        trace.putAll(fields.map {
          case (k, V.StringValue(v)) => k -> StringValue(v)
          case (k, V.NumberValue(v)) => k -> DoubleValue(v.doubleValue())
          case (k, V.BooleanValue(v)) => k -> BooleanValue(v)
        }: _*)
      override def kernel: F[Kernel] = trace.headers.map(Kernel)
      override def span[A](name: String)(k: F[A]): F[A] = trace.span[A](name)(k)
      override def traceId: F[Option[String]] = trace.traceId
      override def traceUri: F[Option[URI]] = Applicative[F].pure(None)
    }

  implicit def natchezToTrace4Cats[F[_]: Applicative](implicit trace: NatchezTrace[F]): Trace[F] =
    new Trace[F] {
      override def put(key: String, value: AttributeValue): F[Unit] = putAll(key -> value)
      override def putAll(fields: (String, AttributeValue)*): F[Unit] =
        trace.put(fields.map {
          case (k, StringValue(v)) => k -> V.StringValue(v)
          case (k, DoubleValue(v)) => k -> V.NumberValue(v)
          case (k, LongValue(v)) => k -> V.NumberValue(v)
          case (k, BooleanValue(v)) => k -> V.BooleanValue(v)
          case (k, StringList(v)) => k -> V.StringValue(v.toString())
          case (k, BooleanList(v)) => k -> V.StringValue(v.toString())
          case (k, DoubleList(v)) => k -> V.StringValue(v.toString())
          case (k, LongList(v)) => k -> V.StringValue(v.toString())
        }: _*)
      override def span[A](name: String, kind: SpanKind)(fa: F[A]): F[A] = trace.span(name)(fa)
      override def headers(toHeaders: ToHeaders): F[Map[String, String]] = trace.kernel.map(_.toHeaders)
      override def setStatus(status: SpanStatus): F[Unit] = Applicative[F].unit
      override def traceId: F[Option[String]] = trace.traceId
    }
}
