package io.janstenpickle.trace4cats.completer

import cats.Applicative
import cats.effect.concurrent.Ref
import cats.effect.syntax.bracket._
import cats.effect.syntax.concurrent._
import cats.effect.{Concurrent, Resource, Timer}
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.monad._
import fs2.Stream
import fs2.concurrent.Queue
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
    def write(inFlight: Ref[F, Int], queue: Queue[F, CompletedSpan], exporter: SpanExporter[F]): F[Unit] =
      queue.dequeue
        .groupWithin(batchSize, batchTimeout)
        .map(spans => Batch(process, spans.toList))
        .evalMap { batch =>
          exporter.exportBatch(batch).guarantee(inFlight.update(_ - batch.spans.size))
        }
        .compile
        .drain
        .onError {
          case th => Logger[F].warn(th)("Failed to export spans")
        }

    for {
      inFlight <- Resource.liftF(Ref.of(0))
      queue <- Resource.liftF(Queue.circularBuffer[F, CompletedSpan](bufferSize))
      _ <- Resource.make(
        Stream
          .retry(write(inFlight, queue, exporter), 5.seconds, _ + 1.second, Int.MaxValue)
          .compile
          .drain
          .start
      )(
        fiber =>
          Applicative[F].unit
            .whileM_(Timer[F].sleep(50.millis) >> inFlight.get.map(_ != 0)) >> fiber.cancel
      )
    } yield
      new SpanCompleter[F] {
        override def complete(span: CompletedSpan): F[Unit] =
          queue.enqueue1(span) >> inFlight.update { current =>
            if (current == bufferSize) current
            else current + 1
          }
      }
  }
}
