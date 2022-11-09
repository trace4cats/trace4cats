package trace4cats

import cats.Applicative
import cats.effect.kernel.syntax.monadCancel._
import cats.effect.kernel.syntax.spawn._
import cats.effect.kernel.{Deferred, Resource, Temporal}
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

    def exportBatches(stream: Stream[F, Option[CompletedSpan]]): F[Unit] =
      Stream
        .eval(Deferred[F, Unit])
        .flatMap { stop =>
          stream
            // this allows us to optimistically terminate the stream. If the first element is `None`, then terminate
            // immediately, if elements that are `Some` have been seen before then wait for the `groupWithin` driven
            // export process to complete first by setting the `Deferred` and inspecting it after execution
            .evalMapAccumulate[F, Boolean, Option[Option[CompletedSpan]]](false) {
              case (false, None) => stop.complete(()).void.as(false -> None)
              case (true, None) => stop.complete(()).void.as(true -> Some(None))
              case (_, Some(s)) => Applicative[F].pure(true -> Some(Some(s)))
            }
            .map(_._2)
            .unNoneTerminate
            .groupWithin(config.batchSize, config.batchTimeout)
            .evalMap { spans =>
              val allSpans = spans.collect { case Some(span) => span }

              ((if (allSpans.nonEmpty)
                  Stream
                    .retry(
                      exporter.exportBatch(Batch(allSpans)),
                      delay = config.retryConfig.delay,
                      nextDelay = config.retryConfig.nextDelay.calc,
                      maxAttempts = config.retryConfig.maxAttempts
                    )
                    .compile
                    .drain
                    .onError { case th =>
                      Logger[F].warn(th)("Failed to export spans")
                    }
                else Applicative[F].unit) >> stop.tryGet.map {
                case None => Some(())
                case Some(_) => None
              }).uncancelable
            }
        }
        .unNoneTerminate
        .compile
        .drain

    for {
      queue <- Resource.eval(Queue.bounded[F, Option[CompletedSpan]](realBufferSize))
      errorQueue <- Resource.eval(Queue.bounded[F, Boolean](1))
      _ <- Stream
        .fromQueueUnterminated(errorQueue)
        .evalScan(false) {
          case (false, false) =>
            Logger[F].warn(s"Failed to enqueue new span, buffer is full of $realBufferSize").as(true)
          case (true, false) => Applicative[F].pure(true)
          case (_, true) => Applicative[F].pure(false)
        }
        .compile
        .drain
        .background
      _ <- exportBatches(Stream.fromQueueUnterminated(queue)).uncancelable.background
        .onFinalize(
          Logger[F].info("Shutting down queued span completer") >> exportBatches(
            Stream.repeatEval(queue.tryTake).map(_.flatten)
          )
        )
      _ <- Resource.onFinalize(queue.offer(None))
    } yield new SpanCompleter[F] {
      override def complete(span: CompletedSpan.Builder): F[Unit] =
        queue
          .tryOffer(Some(span.build(process)))
          .flatMap(errorQueue.tryOffer)
          .void
    }
  }
}
