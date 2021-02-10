package io.janstenpickle.trace4cats.natchez.conversions

import java.net.URI

import _root_.natchez.{Kernel, Trace => NatchezTrace, TraceValue => V}
import cats.Applicative
import cats.syntax.functor._
import io.janstenpickle.trace4cats.inject.Trace
import io.janstenpickle.trace4cats.model.AttributeValue.{BooleanValue, DoubleValue, StringValue}
import io.janstenpickle.trace4cats.natchez.KernelConverter

trait Trace4CatsToNatchez {
  implicit def trace4CatsToNatchez[F[_]: Applicative](implicit trace: Trace[F]): NatchezTrace[F] =
    new NatchezTrace[F] {
      override def put(fields: (String, V)*): F[Unit] =
        trace.putAll(fields.map {
          case (k, V.StringValue(v)) => k -> StringValue(v)
          case (k, V.NumberValue(v)) => k -> DoubleValue(v.doubleValue())
          case (k, V.BooleanValue(v)) => k -> BooleanValue(v)
        }: _*)
      override def kernel: F[Kernel] = trace.headers.map(KernelConverter.to)
      override def span[A](name: String)(k: F[A]): F[A] = trace.span[A](name)(k)
      override def traceId: F[Option[String]] = trace.traceId
      override def traceUri: F[Option[URI]] = Applicative[F].pure(None)
    }
}
