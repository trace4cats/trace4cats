package io.janstenpickle.trace4cats.inject.zio

import io.janstenpickle.trace4cats.{Span, ToHeaders}
import io.janstenpickle.trace4cats.inject.Trace
import io.janstenpickle.trace4cats.model.{AttributeValue, SpanKind, SpanStatus, TraceHeaders}
import zio.{Task, ZIO}
import zio.interop.catz._
import cats.syntax.show._

class ZIOTracer extends Trace[ZIOTrace] {
  override def put(key: String, value: AttributeValue): ZIOTrace[Unit] =
    ZIO.environment[Span[Task]].flatMap(_.put(key, value))

  override def putAll(fields: (String, AttributeValue)*): ZIOTrace[Unit] =
    ZIO.environment[Span[Task]].flatMap(_.putAll(fields: _*))

  override def span[A](name: String, kind: SpanKind)(fa: ZIOTrace[A]): ZIOTrace[A] =
    ZIO.environment[Span[Task]].flatMap(_.child(name, kind).use(fa.provide))

  override def headers(toHeaders: ToHeaders): ZIOTrace[TraceHeaders] =
    ZIO.environment[Span[Task]].map { s =>
      toHeaders.fromContext(s.context)
    }

  override def setStatus(status: SpanStatus): ZIOTrace[Unit] =
    ZIO.environment[Span[Task]].flatMap(_.setStatus(status))

  override def traceId: ZIOTrace[Option[String]] =
    ZIO.environment[Span[Task]].map { s =>
      Some(s.context.traceId.show)
    }

  def lens[R](f: R => Span[Task], g: (R, Span[Task]) => R): Trace[ZIO[R, Throwable, *]] =
    new Trace[ZIO[R, Throwable, *]] {
      override def put(key: String, value: AttributeValue): ZIO[R, Throwable, Unit] =
        ZIO.environment[R].flatMap { r =>
          f(r).put(key, value)
        }

      override def putAll(fields: (String, AttributeValue)*): ZIO[R, Throwable, Unit] =
        ZIO.environment[R].flatMap { r =>
          f(r).putAll(fields: _*)
        }

      override def span[A](name: String, kind: SpanKind)(fa: ZIO[R, Throwable, A]): ZIO[R, Throwable, A] =
        ZIO.environment[R].flatMap { r =>
          f(r).child(name, kind).use(s => fa.provide(g(r, s)))
        }

      override def headers(toHeaders: ToHeaders): ZIO[R, Throwable, TraceHeaders] =
        ZIO.environment[R].flatMap { r =>
          ZIO.effectTotal(toHeaders.fromContext(f(r).context))
        }

      override def setStatus(status: SpanStatus): ZIO[R, Throwable, Unit] =
        ZIO.environment[R].flatMap { r =>
          f(r).setStatus(status)
        }

      override def traceId: ZIO[R, Throwable, Option[String]] =
        ZIO.environment[R].flatMap { r =>
          ZIO.effectTotal(Some(f(r).context.traceId.show))
        }
    }
}
