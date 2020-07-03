package io.janstenpickle.trace4cats.completer

import cats.effect.syntax.concurrent._
import cats.effect.{Concurrent, Resource, Timer}
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import cats.instances.option._
import cats.syntax.functor._
import fs2.Stream
import fs2.concurrent.{InspectableQueue, Queue}
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanExporter}
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}

import scala.concurrent.duration._

object QueuedSpanCompleter {
  def apply[F[_]: Concurrent: Timer: Logger](
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
        .onError {
          case th =>
            Logger[F].warn(th)("Failed to export spans")
        }

    def drain(queue: InspectableQueue[F, CompletedSpan], exporter: SpanExporter[F]): F[Unit] =
      queue.getSize
        .flatMap(queue.tryDequeueChunk1)
        .flatMap(_.traverse { chunk =>
          exporter.exportBatch(Batch(process, chunk.toList))
        }.void)

    for {
      queue <- Resource.liftF(InspectableQueue.circularBuffer[F, CompletedSpan](bufferSize))
      _ <- Resource.make(
        Stream
          .retry(write(queue, exporter), 5.seconds, _ + 1.second, Int.MaxValue)
          .compile
          .drain
          .start
      )(_.cancel >> drain(queue, exporter))
    } yield
      new SpanCompleter[F] {
        override def complete(span: CompletedSpan): F[Unit] = queue.enqueue1(span)
      }
  }
}
