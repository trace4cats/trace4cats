package trace4cats

import cats.effect.kernel.{Resource, Temporal}
import fs2.Chunk
import org.typelevel.log4cats.Logger
import trace4cats.dynamic.HotswapSpanCompleter

object QueuedHotswapSpanCompleter {
  def apply[F[_]: Temporal: Logger](
    process: TraceProcess,
    exporter: SpanExporter[F, Chunk],
    config: CompleterConfig
  ): Resource[F, HotswapSpanCompleter[F, CompleterConfig]] =
    HotswapSpanCompleter[F, CompleterConfig](config)(QueuedSpanCompleter(process, exporter, _))

}
