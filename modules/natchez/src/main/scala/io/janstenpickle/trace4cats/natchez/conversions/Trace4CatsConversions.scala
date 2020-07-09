package io.janstenpickle.trace4cats.natchez.conversions

import _root_.natchez.{Kernel, Trace => NatchezTrace, TraceValue => V}
import cats.syntax.functor._
import cats.{Applicative, Functor}
import io.janstenpickle.trace4cats.ToHeaders
import io.janstenpickle.trace4cats.inject.Trace
import io.janstenpickle.trace4cats.model.AttributeValue.{BooleanValue, DoubleValue, LongValue, StringValue}
import io.janstenpickle.trace4cats.model.{AttributeValue, SpanKind, SpanStatus}

trait Trace4CatsConversions {
  implicit def trace4CatsToNatchez[F[_]: Functor](implicit trace: Trace[F]): NatchezTrace[F] = new NatchezTrace[F] {
    override def put(fields: (String, V)*): F[Unit] =
      trace.put(fields.map {
        case (k, V.StringValue(v)) => k -> StringValue(v)
        case (k, V.NumberValue(v)) => k -> DoubleValue(v.doubleValue())
        case (k, V.BooleanValue(v)) => k -> BooleanValue(v)
      }: _*)
    override def kernel: F[Kernel] = trace.headers.map(Kernel)
    override def span[A](name: String)(k: F[A]): F[A] = trace.span[A](name)(k)
  }

  implicit def natchezToTrace4Cats[F[_]: Applicative](implicit trace: NatchezTrace[F]): Trace[F] = new Trace[F] {
    override def put(key: String, value: AttributeValue): F[Unit] = put(key -> value)
    override def put(fields: (String, AttributeValue)*): F[Unit] =
      trace.put(fields.map {
        case (k, StringValue(v)) => k -> V.StringValue(v)
        case (k, DoubleValue(v)) => k -> V.NumberValue(v)
        case (k, LongValue(v)) => k -> V.NumberValue(v)
        case (k, BooleanValue(v)) => k -> V.BooleanValue(v)
      }: _*)
    override def span[A](name: String, kind: SpanKind)(fa: F[A]): F[A] = trace.span(name)(fa)
    override def headers(toHeaders: ToHeaders): F[Map[String, String]] = trace.kernel.map(_.toHeaders)
    override def setStatus(status: SpanStatus): F[Unit] = Applicative[F].unit
  }
}
