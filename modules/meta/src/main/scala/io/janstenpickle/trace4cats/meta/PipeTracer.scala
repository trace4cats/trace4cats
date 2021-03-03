package io.janstenpickle.trace4cats.meta

import cats.Applicative
import cats.effect.kernel.{Async, Deferred}
import cats.effect.std.Random
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.{Chunk, Pipe, Stream}
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model._

object PipeTracer {
  private final val spanName = "trace4cats.receive.batch"
  private final val spanKind = SpanKind.Consumer

  def apply[F[_]: Async](
    attributes: Map[String, AttributeValue],
    process: TraceProcess,
    sampler: SpanSampler[F],
  ): Pipe[F, CompletedSpan, CompletedSpan] = {
    implicit val random: Random[F] = Random.javaUtilConcurrentThreadLocalRandom
    _.chunks
      .flatMap { batch =>
        Stream.evalUnChunk(for {
          context <- SpanContext.root[F]
          sample <- sampler.shouldSample(None, context.traceId, spanName, spanKind)
          spans <- sample match {
            case SampleDecision.Drop => Applicative[F].pure(batch)
            case SampleDecision.Include =>
              val (batchSize, links) = MetaTraceUtil.extractMetadata(batch)

              for {
                metaSpanPromise <- Deferred[F, CompletedSpan]
                spans <- MetaTraceUtil
                  .trace[F](
                    context,
                    spanName,
                    spanKind,
                    Map[String, AttributeValue]("batch.size" -> batchSize) ++ attributes,
                    links,
                    builder => metaSpanPromise.complete(builder.build(process)).void
                  )
                  .use(meta => Applicative[F].pure(batch.map(span => span.copy(metaTrace = Some(meta)))))
                metaSpan <- metaSpanPromise.get
              } yield Chunk.concat(List(spans, Chunk.singleton(metaSpan)), spans.size + 1)
          }
        } yield spans)
      }
  }

}
