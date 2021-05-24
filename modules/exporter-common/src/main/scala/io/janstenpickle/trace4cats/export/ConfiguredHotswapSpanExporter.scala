package io.janstenpickle.trace4cats.`export`

import cats.effect.kernel.{Concurrent, Resource}
import cats.kernel.Eq
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch

trait ConfiguredHotswapSpanExporter[F[_], G[_], A] extends SpanExporter[F, G] {
  def updateConfig(config: A): F[Boolean]
}

object ConfiguredHotswapSpanExporter {
  def apply[F[_]: Concurrent, G[_], A: Eq](
    initialConfig: A,
    makeExporter: A => Resource[F, SpanExporter[F, G]]
  ): Resource[F, ConfiguredHotswapSpanExporter[F, G, A]] =
    HotswapSpanExporter[F, G, A](initialConfig, makeExporter(initialConfig)).map { underlying =>
      new ConfiguredHotswapSpanExporter[F, G, A] {
        override def updateConfig(config: A): F[Boolean] = underlying.update(config, makeExporter(config))
        override def exportBatch(batch: Batch[G]): F[Unit] = underlying.exportBatch(batch)
      }
    }
}
