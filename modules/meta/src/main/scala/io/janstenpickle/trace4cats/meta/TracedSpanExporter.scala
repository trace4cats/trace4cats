package io.janstenpickle.trace4cats.meta

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.{Concurrent, Resource, Timer}
import cats.syntax.apply._
import fs2.Chunk
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.`export`.QueuedSpanCompleter
import io.janstenpickle.trace4cats.kernel.{BuildInfo, SpanExporter, SpanSampler}
import io.janstenpickle.trace4cats.model._

import scala.concurrent.duration._

object TracedSpanExporter {
  def apply[F[_]: Concurrent: Timer: Logger](
    name: String,
    attributes: List[(String, AttributeValue)],
    process: TraceProcess,
    sampler: SpanSampler[F],
    underlying: SpanExporter[F, Chunk],
    bufferSize: Int = 2000,
    batchSize: Int = 50,
    batchTimeout: FiniteDuration = 5.seconds
  ): Resource[F, SpanExporter[F, Chunk]] =
    QueuedSpanCompleter[F](process, underlying, bufferSize, batchSize, batchTimeout).map { completer =>
      new SpanExporter[F, Chunk] {
        override def exportBatch(batch: Batch[Chunk]): F[Unit] =
          Span.root[F]("trace4cats.export.batch", SpanKind.Producer, sampler, completer).use { meta =>
            meta.context.traceFlags.sampled match {
              case SampleDecision.Drop => underlying.exportBatch(batch)
              case SampleDecision.Include =>
                val (batchSize, links, spans) = BatchUtil.extractMetadata(batch.spans, meta.context)

                meta.putAll(
                  List[(String, AttributeValue)](
                    "exporter.name" -> name,
                    "batch.size" -> batchSize,
                    "trace4cats.version" -> BuildInfo.version
                  ) ++ attributes: _*
                ) *> NonEmptyList
                  .fromList(links)
                  .fold(Applicative[F].unit)(meta.addLinks) *> underlying
                  .exportBatch(Batch(spans))
            }
          }
      }
    }
}
