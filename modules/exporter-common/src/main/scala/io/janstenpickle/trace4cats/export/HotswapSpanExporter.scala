package io.janstenpickle.trace4cats.`export`

import cats.effect.kernel.{Resource, Temporal}
import cats.kernel.Eq
import io.janstenpickle.trace4cats.hotswap.HotswapConstructor
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch

trait HotswapSpanExporter[F[_], G[_], A] extends SpanExporter[F, G] {
  def update(config: A): F[Boolean]
  def getConfig: F[A]
}

object HotswapSpanExporter {
  def apply[F[_]: Temporal, G[_], A: Eq](
    initialId: A,
    makeExporter: A => Resource[F, SpanExporter[F, G]]
  ): Resource[F, HotswapSpanExporter[F, G, A]] =
    HotswapConstructor[F, A, SpanExporter[F, G]](initialId, makeExporter).map { hotswap =>
      new HotswapSpanExporter[F, G, A] {
        override def update(config: A): F[Boolean] = hotswap.swap(config)
        override def getConfig: F[A] = hotswap.getA
        override def exportBatch(batch: Batch[G]): F[Unit] = hotswap.getB.use(_.exportBatch(batch))
      }
    }
}
