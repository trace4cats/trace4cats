package io.janstenpickle.trace4cats.meta

import cats.Monad
import cats.data.NonEmptyList
import cats.effect.kernel.Resource.ExitCase
import cats.effect.kernel.{Clock, Resource}
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.Chunk
import io.janstenpickle.trace4cats.kernel.BuildInfo
import io.janstenpickle.trace4cats.model._

object MetaTraceUtil {
  def trace[F[_]: Monad: Clock](
    context: SpanContext,
    spanName: String,
    spanKind: SpanKind,
    attributes: Map[String, AttributeValue],
    links: Option[NonEmptyList[Link]],
    onFinish: CompletedSpan.Builder => F[Unit]
  ): Resource[F, MetaTrace] = {

    lazy val (ctx, lnks) = links match {
      case None => (context, links)
      case Some(NonEmptyList(head, tail)) =>
        (
          context.copy(traceId = head.traceId, parent = Some(Parent(head.spanId, isRemote = false))),
          NonEmptyList.fromList(tail)
        )
    }

    Resource
      .makeCase(Clock[F].realTimeInstant) { (start, exit) =>
        Clock[F].realTimeInstant.flatMap { end =>
          val status = exit match {
            case ExitCase.Succeeded => SpanStatus.Ok
            case ExitCase.Errored(e) => SpanStatus.Internal(e.getMessage)
            case ExitCase.Canceled => SpanStatus.Cancelled
          }

          val metaSpan = CompletedSpan
            .Builder(
              ctx,
              spanName,
              spanKind,
              start,
              end,
              attributes.updated("trace4cats.version", BuildInfo.version),
              status,
              lnks,
              None
            )

          onFinish(metaSpan)
        }

      }
      .as(MetaTrace(ctx.traceId, ctx.spanId))
  }

  def extractMetadata(batch: Chunk[CompletedSpan]): (Int, Option[NonEmptyList[Link]]) = {
    val (batchSize, links) = batch.foldLeft((0, Set.empty[Link])) { case ((count, links), span) =>
      val updatedLinks = span.metaTrace match {
        case Some(meta) => links + Link(meta.traceId, meta.spanId)
        case None => links
      }

      (count + 1, updatedLinks)
    }

    (batchSize, NonEmptyList.fromList(links.toList))

  }
}
