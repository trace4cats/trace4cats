package trace4cats.samplling.tail

import cats.Functor
import cats.syntax.functor._
import fs2.{Chunk, Pipe, Stream}
import trace4cats.model.{Batch, CompletedSpan}

object TailSamplingPipe {
  def apply[F[_]: Functor](sampler: TailSpanSampler[F, Chunk]): Pipe[F, CompletedSpan, CompletedSpan] =
    _.chunks.flatMap { spans =>
      Stream.evalUnChunk(sampler.sampleBatch(Batch(spans)).map(_.spans))
    }
}
