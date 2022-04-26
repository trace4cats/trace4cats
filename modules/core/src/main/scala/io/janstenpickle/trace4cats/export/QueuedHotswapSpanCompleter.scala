package io.janstenpickle.trace4cats.`export`

import cats.effect.kernel.{Resource, Temporal}
import fs2.Chunk
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.TraceProcess
import org.typelevel.log4cats.Logger

object QueuedHotswapSpanCompleter {
  def apply[F[_]: Temporal: Logger](
    process: TraceProcess,
    exporter: SpanExporter[F, Chunk],
    config: CompleterConfig
  ): Resource[F, HotswapSpanCompleter[F, CompleterConfig]] =
    HotswapSpanCompleter[F, CompleterConfig](config)(QueuedSpanCompleter(process, exporter, _))

}
