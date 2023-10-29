package trace4cats

import cats.Applicative
import cats.effect.kernel.syntax.monadCancel._
import cats.effect.kernel.syntax.spawn._
import cats.effect.kernel.{Resource, Temporal}
import cats.effect.std.Queue
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.concurrent.Channel
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
            .handleErrorWith { th =>
              Logger[F].warn(th)("Failed to export spans")
            }
            .uncancelable
        }
        .compile
        .drain

    for {
      channel <- Resource.eval(Channel.bounded[F, CompletedSpan](realBufferSize))
      errorQueue <- Resource.eval(Queue.bounded[F, Either[Channel.Closed, Boolean]](1))
      _ <- Stream
        .fromQueueUnterminated(errorQueue)
        .evalScan(false) {
          case (false, Right(false)) =>
            Logger[F].warn(s"Failed to enqueue new span, buffer is full of $realBufferSize").as(true)
          case (false, Left(_)) =>
            Logger[F].warn(s"Failed to enqueue new span, channel is closed").as(true)
          case (true, _) => Applicative[F].pure(true)
          case (_, Right(true)) => Applicative[F].pure(false)
        }
        .compile
        .drain
        .background
      exportFiber <- exportBatches(channel.stream).uncancelable.background
        .onFinalize(Logger[F].info("Shut down queued span completer"))
      _ <- Resource.onFinalize(exportFiber.void) // join fiber to ensure everything was sent
      _ <- Resource.onFinalize(channel.close.void)
    } yield new SpanCompleter[F] {
      override def complete(span: CompletedSpan.Builder): F[Unit] =
        channel
          .trySend(span.build(process))
          .flatMap(errorQueue.tryOffer)
          .void
    }
  }
}
