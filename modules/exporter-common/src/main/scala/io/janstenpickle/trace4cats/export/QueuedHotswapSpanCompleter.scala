package io.janstenpickle.trace4cats.`export`

import cats.effect.kernel.{Resource, Temporal}
import fs2.Chunk
import io.janstenpickle.trace4cats.kernel.{SpanCompleter, SpanExporter}
import io.janstenpickle.trace4cats.model.{CompletedSpan, TraceProcess}
import org.typelevel.log4cats.Logger

trait QueuedHotswapSpanCompleter[F[_]] extends SpanCompleter[F] {
  def updateConfig(config: CompleterConfig): F[Boolean]
}

object QueuedHotswapSpanCompleter {

  def apply[F[_]: Temporal: Logger](
    process: TraceProcess,
    exporter: SpanExporter[F, Chunk],
    config: CompleterConfig
  ): Resource[F, QueuedHotswapSpanCompleter[F]] = {
    def makeCompleter(newConfig: CompleterConfig): Resource[F, SpanCompleter[F]] =
      QueuedSpanCompleter(process, exporter, newConfig)

    HotswapSpanCompleter[F, CompleterConfig](config, makeCompleter(config)).map { underlying =>
      new QueuedHotswapSpanCompleter[F] {
        override def updateConfig(config: CompleterConfig): F[Boolean] =
          underlying.update(config, makeCompleter(config))
        override def complete(span: CompletedSpan.Builder): F[Unit] = underlying.complete(span)
      }
    }
  }
}
