package trace4cats.kernel

import cats.kernel.Monoid
import cats.syntax.foldable._
import cats.syntax.parallel._
import cats.{Applicative, Apply, Parallel}
import trace4cats.model.CompletedSpan
import trace4cats.model.CompletedSpan.Builder

import scala.collection.compat._

trait SpanCompleter[F[_]] {
  def complete(span: CompletedSpan.Builder): F[Unit]
}

object SpanCompleter extends LowPrioritySpanCompleterInstances {
  implicit def spanCompleterMonoidFromParallel[F[_]: Applicative: Parallel]: Monoid[SpanCompleter[F]] =
    new Monoid[SpanCompleter[F]] {
      override def combine(x: SpanCompleter[F], y: SpanCompleter[F]): SpanCompleter[F] =
        new SpanCompleter[F] {
          override def complete(span: CompletedSpan.Builder): F[Unit] =
            Parallel.parMap2(x.complete(span), y.complete(span))((_, _) => ())
        }

      override def empty: SpanCompleter[F] = SpanCompleter.empty[F]

      override def combineAllOption(as: IterableOnce[SpanCompleter[F]]): Option[SpanCompleter[F]] =
        if (as.iterator.isEmpty) None
        else Some(combineAll(as))

      override def combineAll(as: IterableOnce[SpanCompleter[F]]): SpanCompleter[F] =
        new SpanCompleter[F] {
          override def complete(span: CompletedSpan.Builder): F[Unit] =
            as.iterator.toList.parTraverse_(_.complete(span))
        }
    }
}

trait LowPrioritySpanCompleterInstances {
  implicit def spanCompleterMonoidFromApply[F[_]: Applicative]: Monoid[SpanCompleter[F]] =
    new Monoid[SpanCompleter[F]] {
      override def combine(x: SpanCompleter[F], y: SpanCompleter[F]): SpanCompleter[F] =
        new SpanCompleter[F] {
          override def complete(span: Builder): F[Unit] =
            Apply[F].map2(x.complete(span), y.complete(span))((_, _) => ())
        }

      override def empty: SpanCompleter[F] = SpanCompleter.empty[F]

      override def combineAllOption(as: IterableOnce[SpanCompleter[F]]): Option[SpanCompleter[F]] =
        if (as.iterator.isEmpty) None
        else Some(combineAll(as))

      override def combineAll(as: IterableOnce[SpanCompleter[F]]): SpanCompleter[F] =
        new SpanCompleter[F] {
          override def complete(span: CompletedSpan.Builder): F[Unit] =
            as.iterator.toList.traverse_(_.complete(span))
        }
    }

  def empty[F[_]: Applicative]: SpanCompleter[F] =
    new SpanCompleter[F] {
      override def complete(span: CompletedSpan.Builder): F[Unit] = Applicative[F].unit
    }
}
