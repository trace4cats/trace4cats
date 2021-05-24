package io.janstenpickle.trace4cats.`export`

import cats.Applicative
import cats.effect.kernel.syntax.monadCancel._
import cats.effect.kernel.syntax.spawn._
import cats.effect.kernel.{Clock, Ref, Resource, Temporal}
import cats.effect.std.Queue
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.monad._
import fs2.{Chunk, Stream}
import io.janstenpickle.trace4cats.`export`.ExportRetryConfig.NextDelay
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanExporter}
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, TraceProcess}
import org.typelevel.log4cats.Logger

import scala.concurrent.duration._

object QueuedSpanCompleter {
  def apply[F[_]: Temporal: Logger](
    process: TraceProcess,
    exporter: SpanExporter[F, Chunk],
    config: CompleterConfig
  ): Resource[F, SpanCompleter[F]] = {
    val realBufferSize = if (config.bufferSize < config.batchSize * 5) config.batchSize * 5 else config.bufferSize

    for {
      inFlight <- Resource.eval(Ref.of(0))
      hasLoggedWarn <- Resource.eval(Ref.of(false))
      queue <- Resource.eval(Queue.bounded[F, CompletedSpan](realBufferSize))
      _ <- Resource.make {
        Stream
          .fromQueueUnterminated(queue)
          .groupWithin(config.batchSize, config.batchTimeout)
          .map(spans => Batch(spans))
          .evalMap { batch =>
            Stream
              .retry(
                exporter.exportBatch(batch),
                delay = config.retryConfig.delay,
                nextDelay = t =>
                  config.retryConfig.nextDelay match {
                    case NextDelay.Constant(d) => t + d
                    case NextDelay.Exponential => t + t
                  },
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
      }(fiber => Clock[F].sleep(50.millis).whileM_(inFlight.get.map(_ != 0)) >> fiber.cancel)
    } yield new SpanCompleter[F] {
      override def complete(span: CompletedSpan.Builder): F[Unit] = {
        val enqueue = queue.offer(span.build(process)) >> inFlight.update { current =>
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
