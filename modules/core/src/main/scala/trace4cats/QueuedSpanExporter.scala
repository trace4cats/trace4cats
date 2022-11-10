package trace4cats

import cats.Parallel
import cats.effect.kernel.{Resource, Temporal}
import cats.effect.syntax.monadCancel._
import cats.effect.syntax.spawn._
import cats.effect.syntax.temporal._
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.parallel._
import fs2.concurrent.Channel
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
      def exportBatches(stream: Stream[F, Batch[Chunk]]): F[Unit] =
        stream.evalMap(exporter.exportBatch(_).uncancelable).compile.drain

      for {
        channel <- Resource.eval(Channel.bounded[F, Batch[Chunk]](bufferSize))
        _ <- exportBatches(channel.stream).uncancelable.background
        _ <- Resource.onFinalize(channel.close.void)
      } yield new StreamSpanExporter[F] {
        override def exportBatch(batch: Batch[Chunk]): F[Unit] =
          channel
            .send(batch)
            .void
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
