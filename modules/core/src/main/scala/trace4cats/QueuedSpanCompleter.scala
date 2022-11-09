package trace4cats

import cats.Applicative
import cats.effect.kernel.syntax.spawn._
import cats.effect.kernel.{Ref, Resource, Temporal}
import cats.effect.std.Queue
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.{Chunk, Stream}
import org.typelevel.log4cats.Logger

object QueuedSpanCompleter {
  def apply[F[_]: Temporal: Logger](
    process: TraceProcess,
    exporter: SpanExporter[F, Chunk],
    config: CompleterConfig
  ): Resource[F, SpanCompleter[F]] = {
    val realBufferSize = if (config.bufferSize < config.batchSize * 5) config.batchSize * 5 else config.bufferSize

    def exportBatches(stream: Stream[F, CompletedSpan]): F[Unit] =
      stream
        .groupWithin(config.batchSize, config.batchTimeout)
        .evalMap { spans =>
          Stream
            .retry(
              exporter.exportBatch(Batch(spans)),
              delay = config.retryConfig.delay,
              nextDelay = config.retryConfig.nextDelay.calc,
              maxAttempts = config.retryConfig.maxAttempts
            )
            .compile
            .drain
            .onError { case th =>
              Logger[F].warn(th)("Failed to export spans")
            }
        }
        .compile
        .drain

    for {
      hasLoggedWarn <- Resource.eval(Ref.of(false))
      queue <- Resource.eval(Queue.bounded[F, CompletedSpan](realBufferSize))
      _ <- exportBatches(Stream.fromQueueUnterminated(queue)).background.onFinalize(
        exportBatches(
          Stream
            .repeatEval(queue.tryTake)
            .unNoneTerminate
        )
      )
    } yield new SpanCompleter[F] {
      override def complete(span: CompletedSpan.Builder): F[Unit] = {

        val warnLog = hasLoggedWarn.get
          .map(!_)
          .ifM(
            Logger[F].warn(s"Failed to enqueue new span, buffer is full of $realBufferSize") >> hasLoggedWarn.set(true),
            Applicative[F].unit,
          )

        queue
          .tryOffer(span.build(process))
          .ifM(hasLoggedWarn.set(false), warnLog)
      }
    }
  }
}
