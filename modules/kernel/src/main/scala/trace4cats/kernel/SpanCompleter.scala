package trace4cats.kernel

import cats.syntax.parallel._
import cats.{Applicative, Parallel}
import trace4cats.model.CompletedSpan
import trace4cats.model.CompletedSpan.Builder

trait SpanCompleter[F[_]] {
  def complete(span: CompletedSpan.Builder): F[Unit]
}

object SpanCompleter {
  def empty[F[_]: Applicative]: SpanCompleter[F] =
    new SpanCompleter[F] {
      override def complete(span: CompletedSpan.Builder): F[Unit] = Applicative[F].unit
    }

  def combined[F[_]: Parallel](completers: List[SpanCompleter[F]]): SpanCompleter[F] = new SpanCompleter[F] {
    override def complete(span: Builder): F[Unit] = completers.parTraverse_(_.complete(span))
  }
}
