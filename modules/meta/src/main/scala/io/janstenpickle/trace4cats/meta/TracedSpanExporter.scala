package io.janstenpickle.trace4cats.meta

import cats.Applicative
import cats.effect.kernel.{Async, Deferred}
import cats.effect.std.Random
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Chunk
import io.janstenpickle.trace4cats.`export`.StreamSpanExporter
import io.janstenpickle.trace4cats.kernel.{SpanExporter, SpanSampler}
import io.janstenpickle.trace4cats.model._

object TracedSpanExporter {
  private final val spanName = "trace4cats.export.batch"
  private final val spanKind = SpanKind.Producer

  def apply[F[_]: Async](
    name: String,
    attributes: Map[String, AttributeValue],
    process: TraceProcess,
    sampler: SpanSampler[F],
    underlying: SpanExporter[F, Chunk],
  ): StreamSpanExporter[F] = {
    implicit val random: Random[F] = Random.javaUtilConcurrentThreadLocalRandom
    new StreamSpanExporter[F] {
      override def exportBatch(batch: Batch[Chunk]): F[Unit] = for {
        context <- SpanContext.root[F]
        sample <- sampler.shouldSample(None, context.traceId, spanName, spanKind)
        _ <- sample match {
          case SampleDecision.Drop => underlying.exportBatch(batch)
          case SampleDecision.Include =>
            val (batchSize, links) = MetaTraceUtil.extractMetadata(batch.spans)

            for {
              metaSpanPromise <- Deferred[F, CompletedSpan]

              spans <- MetaTraceUtil
                .trace[F](
                  context,
                  spanName,
                  spanKind,
                  Map[String, AttributeValue]("exporter.name" -> name, "batch.size" -> batchSize) ++ attributes,
                  links,
                  builder => metaSpanPromise.complete(builder.build(process)).void
                )
                .use(meta => Applicative[F].pure(batch.spans.map(span => span.copy(metaTrace = Some(meta)))))

              metaSpan <- metaSpanPromise.get
              _ <- exportBatch(Batch(Chunk.concat(List(spans, Chunk.singleton(metaSpan)), spans.size + 1)))
            } yield ()

            MetaTraceUtil
              .trace[F](
                context,
                spanName,
                spanKind,
                Map[String, AttributeValue]("exporter.name" -> name, "batch.size" -> batchSize) ++ attributes,
                links,
                builder => underlying.exportBatch(Batch(Chunk.singleton(builder.build(process))))
              )
              .use(meta => underlying.exportBatch(Batch(batch.spans.map(span => span.copy(metaTrace = Some(meta))))))
        }
      } yield ()
    }
  }
}
