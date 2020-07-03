package io.janstenpickle.trace4cats.exporter

import cats.effect.concurrent.Ref
import cats.effect.syntax.bracket._
import cats.effect.syntax.concurrent._
import cats.effect.{Concurrent, Resource, Timer}
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.monad._
import cats.syntax.parallel._
import cats.{Applicative, Parallel}
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch

import scala.concurrent.duration._

object BufferingExporter {
  def apply[F[_]: Concurrent: Timer: Parallel: Logger](
    bufferSize: Int,
    exporters: List[(String, SpanExporter[F])]
  ): Resource[F, SpanExporter[F]] = {
    def buffer(exporter: SpanExporter[F]): Resource[F, SpanExporter[F]] =
      for {
        inFlight <- Resource.liftF(Ref.of[F, Int](0))
        queue <- Resource.liftF(Queue.circularBuffer[F, Batch](bufferSize))
        _ <- Resource.make(
          queue.dequeue.evalMap(exporter.exportBatch(_).guarantee(inFlight.update(_ - 1))).compile.drain.start
        )(
          fiber =>
            Applicative[F].unit
              .whileM_(Timer[F].sleep(50.millis) >> inFlight.get.map(_ != 0)) >> fiber.cancel
        )
      } yield
        new SpanExporter[F] {
          override def exportBatch(batch: Batch): F[Unit] = queue.enqueue1(batch) >> inFlight.update { current =>
            if (current == bufferSize) current
            else current + 1
          }
        }

    exporters
      .map {
        case (name, exporter) =>
          SpanExporter.handleErrors[F, Throwable](exporter) {
            case th => Logger[F].warn(th)(s"Failed to export span batch with $name")
          }
      }
      .parTraverse(buffer)
      .map(_.combineAll)
  }

}
