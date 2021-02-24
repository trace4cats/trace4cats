package io.janstenpickle.trace4cats.`export`

import cats.Applicative
import cats.effect.concurrent.Ref
import cats.effect.syntax.bracket._
import cats.effect.syntax.concurrent._
import cats.effect.{Concurrent, Resource, Timer}
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.monad._
import fs2.{Chunk, Stream}
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanExporter}
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}

import scala.concurrent.duration._

object QueuedSpanCompleter {
  def apply[F[_]: Concurrent: Timer: Logger](
    process: TraceProcess,
    exporter: SpanExporter[F, Chunk],
    config: CompleterConfig
  ): Resource[F, SpanCompleter[F]] = {
    val realBufferSize = if (config.bufferSize < config.batchSize * 5) config.batchSize * 5 else config.bufferSize

    for {
      inFlight <- Resource.liftF(Ref.of(0))
      hasLoggedWarn <- Resource.liftF(Ref.of(false))
      queue <- Resource.liftF(Queue.bounded[F, CompletedSpan](realBufferSize))
      _ <- Resource.make {
        queue.dequeue
          .groupWithin(config.batchSize, config.batchTimeout)
          .map(spans => Batch(spans))
          .evalMap { batch =>
            Stream
              .retry(
                exporter.exportBatch(batch),
                delay = config.retryConfig.delay,
                nextDelay = config.retryConfig.nextDelay,
                maxAttempts = config.retryConfig.maxAttempts
              )
              .compile
              .drain
              .onError { case th =>
                Logger[F].warn(th)("Failed to export spans")
              }
              .guarantee(inFlight.update(_ - batch.spans.size))
          }
          .compile
          .drain
          .start
      }(fiber => Timer[F].sleep(50.millis).whileM_(inFlight.get.map(_ != 0)) >> fiber.cancel)
    } yield new SpanCompleter[F] {
      override def complete(span: CompletedSpan.Builder): F[Unit] = {
        val enqueue = queue.enqueue1(span.build(process)) >> inFlight.update { current =>
          if (current == realBufferSize) current
          else current + 1
        }

        val warnLog = hasLoggedWarn.get.ifM(
          Logger[F].warn(s"Failed to enqueue new span, buffer is full of $realBufferSize") >> hasLoggedWarn.set(true),
          Applicative[F].unit,
        )

        inFlight.get
          .map(_ == realBufferSize)
          .ifM(warnLog, enqueue >> hasLoggedWarn.set(false))
      }
    }
  }
}
