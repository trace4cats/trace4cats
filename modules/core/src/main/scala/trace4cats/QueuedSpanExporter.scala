package trace4cats

import cats.Parallel
import cats.effect.kernel.{Resource, Temporal}
import cats.effect.std.Queue
import cats.effect.syntax.monadCancel._
import cats.effect.syntax.spawn._
import cats.effect.syntax.temporal._
import cats.syntax.foldable._
import cats.syntax.parallel._
import cats.syntax.traverse._
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
      def exportBatches(stream: Stream[F, Option[Batch[Chunk]]]): F[Unit] =
        stream.evalMap(_.traverse(exporter.exportBatch(_).uncancelable)).unNoneTerminate.compile.drain

      for {
        queue <- Resource.eval(Queue.bounded[F, Option[Batch[Chunk]]](bufferSize))
        _ <- exportBatches(Stream.fromQueueUnterminated(queue)).uncancelable.background
          .onFinalize(exportBatches(Stream.repeatEval(queue.tryTake).map(_.flatten)))
        _ <- Resource.onFinalize(queue.offer(None))
      } yield new StreamSpanExporter[F] {
        override def exportBatch(batch: Batch[Chunk]): F[Unit] =
          queue
            .offer(Some(batch))
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
