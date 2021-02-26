package io.janstenpickle.trace4cats.`export`

import cats.Parallel
import cats.effect.concurrent.Ref
import cats.effect.syntax.bracket._
import cats.effect.syntax.concurrent._
import cats.effect.{Concurrent, Resource, Timer}
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.monad._
import cats.syntax.parallel._
import fs2.Chunk
import fs2.concurrent.Queue
import org.typelevel.log4cats.Logger
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch

import scala.concurrent.duration._

object QueuedSpanExporter {
  def apply[F[_]: Concurrent: Timer: Parallel: Logger](
    bufferSize: Int,
    exporters: List[(String, SpanExporter[F, Chunk])],
    enqueueTimeout: FiniteDuration = 200.millis
  ): Resource[F, StreamSpanExporter[F]] = {
    def buffer(exporter: SpanExporter[F, Chunk]): Resource[F, StreamSpanExporter[F]] =
      for {
        inFlight <- Resource.liftF(Ref.of[F, Int](0))
        queue <- Resource.liftF(Queue.bounded[F, Batch[Chunk]](bufferSize))
        _ <- Resource.make(
          queue.dequeue.evalMap(exporter.exportBatch(_).guarantee(inFlight.update(_ - 1))).compile.drain.start
        )(fiber => Timer[F].sleep(50.millis).whileM_(inFlight.get.map(_ != 0)) >> fiber.cancel)
      } yield new StreamSpanExporter[F] {
        override def exportBatch(batch: Batch[Chunk]): F[Unit] =
          (queue.enqueue1(batch) >> inFlight.update { current =>
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
