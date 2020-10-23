package io.janstenpickle.trace4cats.sampling.tail

import cats.syntax.flatMap._
import cats.{Applicative, Monad}
import fs2.Pipe
import io.janstenpickle.trace4cats.`export`.StreamSpanExporter
import io.janstenpickle.trace4cats.model.Batch

object TailSamplingSpanExporter {
  def apply[F[_]: Monad](underlying: StreamSpanExporter[F], sampler: TailSpanSampler[F]): StreamSpanExporter[F] =
    new StreamSpanExporter[F] {
      override def pipe: Pipe[F, Batch, Unit] =
        _.evalMapChunk { batch =>
          sampler.sampleBatch(batch)
        }.unNone.through(underlying.pipe)

      override def exportBatch(batch: Batch): F[Unit] =
        sampler.sampleBatch(batch).flatMap(_.fold(Applicative[F].unit)(underlying.exportBatch))
    }
}
