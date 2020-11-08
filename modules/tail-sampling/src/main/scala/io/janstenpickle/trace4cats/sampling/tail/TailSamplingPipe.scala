package io.janstenpickle.trace4cats.sampling.tail

import cats.Functor
import fs2.{Chunk, Pipe}
import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan}
import cats.syntax.functor._
import fs2.Stream

object TailSamplingPipe {
  def apply[F[_]: Functor](sampler: TailSpanSampler[F, Chunk]): Pipe[F, CompletedSpan, CompletedSpan] =
    _.chunks.flatMap { spans =>
      Stream.evalUnChunk(sampler.sampleBatch(Batch(spans)).map(_.spans))
    }
}
