package io.janstenpickle.trace4cats.kernel

import cats.kernel.Monoid
import cats.{Applicative, Apply, Parallel}
import io.janstenpickle.trace4cats.model.CompletedSpan

trait SpanCompleter[F[_]] {
  def complete(span: CompletedSpan): F[Unit]
}

object SpanCompleter extends LowPrioritySpanCompleterInstances {
  implicit def spanCompleterMonoidFromParallel[F[_]: Applicative: Parallel]: Monoid[SpanCompleter[F]] =
    new Monoid[SpanCompleter[F]] {
      override def combine(x: SpanCompleter[F], y: SpanCompleter[F]): SpanCompleter[F] =
        new SpanCompleter[F] {
          override def complete(span: CompletedSpan): F[Unit] =
            Parallel.parMap2(x.complete(span), y.complete(span))((_, _) => ())
        }

      override def empty: SpanCompleter[F] = SpanCompleter.empty[F]
    }
}

trait LowPrioritySpanCompleterInstances {
  implicit def spanCompleterMonoidFromApply[F[_]: Applicative]: Monoid[SpanCompleter[F]] =
    new Monoid[SpanCompleter[F]] {
      override def combine(x: SpanCompleter[F], y: SpanCompleter[F]): SpanCompleter[F] =
        new SpanCompleter[F] {
          override def complete(span: CompletedSpan): F[Unit] =
            Apply[F].map2(x.complete(span), y.complete(span))((_, _) => ())
        }

      override def empty: SpanCompleter[F] = SpanCompleter.empty[F]
    }

  def empty[F[_]: Applicative]: SpanCompleter[F] = new SpanCompleter[F] {
    override def complete(span: CompletedSpan): F[Unit] = Applicative[F].unit
  }
}
