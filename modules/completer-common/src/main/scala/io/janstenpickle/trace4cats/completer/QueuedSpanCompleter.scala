package io.janstenpickle.trace4cats.completer

import cats.effect.syntax.concurrent._
import cats.effect.{Concurrent, Resource, Timer}
import fs2.Stream
import fs2.concurrent.Queue
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanExporter}
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}

import scala.concurrent.duration._

object QueuedSpanCompleter {
  def apply[F[_]: Concurrent: Timer](
    process: TraceProcess,
    exporter: SpanExporter[F],
    bufferSize: Int,
    batchSize: Int,
    batchTimeout: FiniteDuration
  ): Resource[F, SpanCompleter[F]] = {
    def write(queue: Queue[F, CompletedSpan], exporter: SpanExporter[F]): F[Unit] =
      queue.dequeue
        .groupWithin(batchSize, batchTimeout)
        .map(spans => Batch(process, spans.toList))
        .evalMap(exporter.exportBatch)
        .compile
        .drain

    for {
      queue <- Resource.liftF(Queue.circularBuffer[F, CompletedSpan](bufferSize))
      _ <- Stream
        .retry(write(queue, exporter), 5.seconds, _ + 1.second, Int.MaxValue)
        .compile
        .drain
        .background
    } yield
      new SpanCompleter[F] {
        override def complete(span: CompletedSpan): F[Unit] = queue.enqueue1(span)
      }
  }
}
