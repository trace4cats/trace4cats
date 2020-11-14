package io.janstenpickle.trace4cats.meta

import fs2.Chunk
import io.janstenpickle.trace4cats.model.{CompletedSpan, Link, MetaTrace, SpanContext}

object BatchUtil {
  def extractMetadata(
    batch: Chunk[CompletedSpan],
    metaContext: SpanContext
  ): (Int, List[Link], Chunk[CompletedSpan]) = {
    val (batchSize, links, spans) = batch.foldLeft((0, List.empty[Link], Array.empty[CompletedSpan])) {
      case ((count, links, acc), span) =>
        val (updatedSpan, updatedLinks) = span.metaTrace match {
          case Some(meta) => (span, Link.Parent(meta.traceId, meta.spanId) :: links)
          case None =>
            (span.copy(metaTrace = Some(MetaTrace(metaContext.traceId, metaContext.spanId))), links)
        }

        (count + 1, updatedLinks, acc :+ updatedSpan)
    }

    (batchSize, links, Chunk.Boxed(spans, 0, batchSize))
  }
}
