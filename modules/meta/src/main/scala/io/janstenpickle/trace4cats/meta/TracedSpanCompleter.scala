package io.janstenpickle.trace4cats.meta

import cats.effect.{Clock, Sync}
import cats.syntax.apply._
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.kernel.{BuildInfo, SpanCompleter, SpanSampler}
import io.janstenpickle.trace4cats.model.{CompletedSpan, MetaTrace, SampleDecision, SpanKind}

object TracedSpanCompleter {
  def apply[F[_]: Sync: Clock](
    name: String,
    sampler: SpanSampler[F],
    underlying: SpanCompleter[F],
  ): SpanCompleter[F] = {
    new SpanCompleter[F] {
      override def complete(span: CompletedSpan.Builder): F[Unit] =
        Span.root[F]("trace4cats.complete.span", SpanKind.Producer, sampler, underlying).use { meta =>
          meta.context.traceFlags.sampled match {
            case SampleDecision.Drop => underlying.complete(span)
            case SampleDecision.Include =>
              meta.putAll("completer.name" -> name, "trace4cats.version" -> BuildInfo.version) *> underlying
                .complete(span.withMetaTrace(MetaTrace(meta.context.traceId, meta.context.spanId)))
          }
        }
    }
  }
}
