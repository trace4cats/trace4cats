package io.janstenpickle.trace4cats.meta

import cats.effect.kernel.Sync
import cats.effect.std.Random
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanSampler}
import io.janstenpickle.trace4cats.model._

object TracedSpanCompleter {
  private final val spanName = "trace4cats.complete.span"
  private final val spanKind = SpanKind.Producer

  def apply[F[_]: Sync](
    name: String,
    sampler: SpanSampler[F],
    underlying: SpanCompleter[F],
  ): SpanCompleter[F] = {
    implicit val random: Random[F] = Random.javaUtilConcurrentThreadLocalRandom
    new SpanCompleter[F] {
      override def complete(span: CompletedSpan.Builder): F[Unit] =
        for {
          context <- SpanContext.root[F]
          sample <- sampler.shouldSample(None, context.traceId, spanName, spanKind)
          _ <- sample match {
            case SampleDecision.Drop => underlying.complete(span)
            case SampleDecision.Include =>
              MetaTraceUtil
                .trace[F](context, spanName, spanKind, Map("completer.name" -> name), None, underlying.complete)
                .use(meta => underlying.complete(span.withMetaTrace(meta)))
          }
        } yield ()
    }
  }
}
