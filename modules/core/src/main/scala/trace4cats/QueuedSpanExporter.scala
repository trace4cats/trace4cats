package trace4cats

import cats.Parallel
import cats.effect.kernel.{Deferred, Resource, Temporal}
import cats.effect.std.Queue
import cats.effect.syntax.monadCancel._
import cats.effect.syntax.spawn._
import cats.effect.syntax.temporal._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.parallel._
import fs2.{Chunk, Stream}
import org.typelevel.log4cats.Logger

import scala.concurrent.duration._

object QueuedSpanExporter {
  def apply[F[_]: Temporal: Parallel: Logger](
    bufferSize: Int,
    exporters: List[(String, SpanExporter[F, Chunk])],
    enqueueTimeout: FiniteDuration = 200.millis
  ): Resource[F, StreamSpanExporter[F]] = {
    def buffer(exporter: SpanExporter[F, Chunk]): Resource[F, StreamSpanExporter[F]] = {
      def exportBatches(
        stream: Stream[F, Batch[Chunk]],
        signal: Option[Deferred[F, Either[Throwable, Unit]]]
      ): F[Unit] = {
        val exportStream = stream.evalMap(exporter.exportBatch(_).uncancelable)

        signal.fold(exportStream)(exportStream.interruptWhen(_)).compile.drain
      }

      for {
        queue <- Resource.eval(Queue.bounded[F, Batch[Chunk]](bufferSize))
        shutdownSignal <- Resource.eval(Deferred[F, Either[Throwable, Unit]])
        _ <- exportBatches(Stream.fromQueueUnterminated(queue), Some(shutdownSignal)).uncancelable.background
          .onFinalize(exportBatches(Stream.repeatEval(queue.tryTake).unNoneTerminate, None))
        _ <- Resource.onFinalize(shutdownSignal.complete(Right(())).void)
      } yield new StreamSpanExporter[F] {
        override def exportBatch(batch: Batch[Chunk]): F[Unit] =
          queue
            .offer(batch)
            .timeoutTo(enqueueTimeout, Logger[F].warn(s"Failed to enqueue span batch in $enqueueTimeout"))
      }
    }

    exporters
      .map { case (name, exporter) =>
        SpanExporter.handleErrors[F, Chunk, Throwable](exporter) { case th =>
          Logger[F].warn(th)(s"Failed to export span batch with $name")
        }
      }
      .parTraverse(buffer)
      .map(_.combineAll)
  }

}
