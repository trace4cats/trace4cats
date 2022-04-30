package trace4cats

import cats.Parallel
import cats.effect.kernel.{Clock, Ref, Resource, Temporal}
import cats.effect.std.Queue
import cats.effect.syntax.monadCancel._
import cats.effect.syntax.spawn._
import cats.effect.syntax.temporal._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.monad._
import cats.syntax.parallel._
import fs2.{Chunk, Stream}
import org.typelevel.log4cats.Logger
import trace4cats.kernel.SpanExporter
import trace4cats.model.Batch

import scala.concurrent.duration._

object QueuedSpanExporter {
  def apply[F[_]: Temporal: Parallel: Logger](
    bufferSize: Int,
    exporters: List[(String, SpanExporter[F, Chunk])],
    enqueueTimeout: FiniteDuration = 200.millis
  ): Resource[F, StreamSpanExporter[F]] = {
    def buffer(exporter: SpanExporter[F, Chunk]): Resource[F, StreamSpanExporter[F]] =
      for {
        inFlight <- Resource.eval(Ref.of[F, Int](0))
        queue <- Resource.eval(Queue.bounded[F, Batch[Chunk]](bufferSize))
        _ <- Resource.make(
          Stream
            .fromQueueUnterminated(queue)
            .evalMap(exporter.exportBatch(_).guarantee(inFlight.update(_ - 1)))
            .compile
            .drain
            .start
        )(fiber => Clock[F].sleep(50.millis).whileM_(inFlight.get.map(_ != 0)) >> fiber.cancel)
      } yield new StreamSpanExporter[F] {
        override def exportBatch(batch: Batch[Chunk]): F[Unit] =
          (queue.offer(batch) >> inFlight.update { current =>
            if (current == bufferSize) current
            else current + 1
          }).timeoutTo(enqueueTimeout, Logger[F].warn(s"Failed to enqueue span batch in $enqueueTimeout"))
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
