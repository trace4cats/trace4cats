package io.janstenpickle.trace4cats.sampling.tail

import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.{Chunk, Pipe, Stream}
import trace4cats.StreamSpanExporter
import trace4cats.kernel.SpanExporter
import trace4cats.model.{Batch, CompletedSpan}

object TailSamplingSpanExporter {
  def apply[F[_]: Monad](underlying: StreamSpanExporter[F], sampler: TailSpanSampler[F, Chunk]): StreamSpanExporter[F] =
    new StreamSpanExporter[F] {
      override def pipe: Pipe[F, CompletedSpan, Unit] =
        _.chunks
          .flatMap { chunk =>
            Stream.evalUnChunk(sampler.sampleBatch(Batch(chunk)).map(_.spans))
          }
          .through(underlying.pipe)

      override def exportBatch(batch: Batch[Chunk]): F[Unit] =
        sampler.sampleBatch(batch).flatMap(underlying.exportBatch)
    }

  def apply[F[_]: Monad, G[_]](underlying: SpanExporter[F, G], sampler: TailSpanSampler[F, G]): SpanExporter[F, G] =
    new SpanExporter[F, G] {
      override def exportBatch(batch: Batch[G]): F[Unit] =
        sampler.sampleBatch(batch).flatMap(underlying.exportBatch)
    }
}
