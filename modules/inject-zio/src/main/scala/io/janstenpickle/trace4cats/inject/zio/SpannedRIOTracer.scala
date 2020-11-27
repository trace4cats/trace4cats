package io.janstenpickle.trace4cats.inject.zio

import io.janstenpickle.trace4cats.{Span, ToHeaders}
import io.janstenpickle.trace4cats.inject.Trace
import io.janstenpickle.trace4cats.model.{AttributeValue, SpanKind, SpanStatus, TraceHeaders}
import zio.{RIO, Task, ZIO}
import zio.interop.catz._
import cats.syntax.show._

class SpannedRIOTracer extends Trace[SpannedRIO] {
  override def put(key: String, value: AttributeValue): SpannedRIO[Unit] =
    ZIO.environment[Span[Task]].flatMap(_.put(key, value))

  override def putAll(fields: (String, AttributeValue)*): SpannedRIO[Unit] =
    ZIO.environment[Span[Task]].flatMap(_.putAll(fields: _*))

  override def span[A](name: String, kind: SpanKind)(fa: SpannedRIO[A]): SpannedRIO[A] =
    ZIO.environment[Span[Task]].flatMap(_.child(name, kind).use(fa.provide))

  override def headers(toHeaders: ToHeaders): SpannedRIO[TraceHeaders] =
    ZIO.environment[Span[Task]].map { s =>
      toHeaders.fromContext(s.context)
    }

  override def setStatus(status: SpanStatus): SpannedRIO[Unit] =
    ZIO.environment[Span[Task]].flatMap(_.setStatus(status))

  override def traceId: SpannedRIO[Option[String]] =
    ZIO.environment[Span[Task]].map { s =>
      Some(s.context.traceId.show)
    }

  def lens[R](f: R => Span[Task], g: (R, Span[Task]) => R): Trace[RIO[R, *]] =
    new Trace[RIO[R, *]] {
      override def put(key: String, value: AttributeValue): RIO[R, Unit] =
        ZIO.environment[R].flatMap { r =>
          f(r).put(key, value)
        }

      override def putAll(fields: (String, AttributeValue)*): RIO[R, Unit] =
        ZIO.environment[R].flatMap { r =>
          f(r).putAll(fields: _*)
        }

      override def span[A](name: String, kind: SpanKind)(fa: RIO[R, A]): RIO[R, A] =
        ZIO.environment[R].flatMap { r =>
          f(r).child(name, kind).use(s => fa.provide(g(r, s)))
        }

      override def headers(toHeaders: ToHeaders): RIO[R, TraceHeaders] =
        ZIO.environment[R].flatMap { r =>
          ZIO.effectTotal(toHeaders.fromContext(f(r).context))
        }

      override def setStatus(status: SpanStatus): RIO[R, Unit] =
        ZIO.environment[R].flatMap { r =>
          f(r).setStatus(status)
        }

      override def traceId: RIO[R, Option[String]] =
        ZIO.environment[R].flatMap { r =>
          ZIO.effectTotal(Some(f(r).context.traceId.show))
        }
    }
}
