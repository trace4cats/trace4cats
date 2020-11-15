package io.janstenpickle.trace4cats.meta

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.{Concurrent, Timer}
import cats.syntax.apply._
import cats.syntax.functor._
import fs2.concurrent.Queue
import fs2.{Pipe, Stream}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.kernel.{BuildInfo, SpanCompleter, SpanSampler}
import io.janstenpickle.trace4cats.model._

object PipeTracer {
  def apply[F[_]: Concurrent: Timer](
    attributes: List[(String, AttributeValue)],
    process: TraceProcess,
    sampler: SpanSampler[F],
    bufferSize: Int = 200,
  ): F[Pipe[F, CompletedSpan, CompletedSpan]] =
    Queue.circularBuffer[F, CompletedSpan](bufferSize).map { queue =>
      val completer = new SpanCompleter[F] {
        override def complete(span: CompletedSpan.Builder): F[Unit] = queue.enqueue1(span.build(process))
      }

      in: Stream[F, CompletedSpan] =>
        in.chunks
          .flatMap { batch =>
            Stream.evalUnChunk(Span.root[F]("trace4cats.receive.batch", SpanKind.Consumer, sampler, completer).use {
              meta =>
                meta.context.traceFlags.sampled match {
                  case SampleDecision.Drop => Applicative[F].pure(batch)
                  case SampleDecision.Include =>
                    val (batchSize, links, spans) = BatchUtil.extractMetadata(batch, meta.context)

                    meta.putAll(
                      List[(String, AttributeValue)](
                        "batch.size" -> batchSize,
                        "trace4cats.version" -> BuildInfo.version
                      ) ++ attributes: _*
                    ) *> NonEmptyList
                      .fromList(links)
                      .fold(Applicative[F].unit)(meta.addLinks)
                      .as(spans)

                }
            })
          }
          .merge(queue.dequeue)
    }
}
