package io.janstenpickle.trace4cats.`export`

import cats.effect.kernel.{Resource, Temporal}
import cats.kernel.Eq
import cats.syntax.applicative._
import io.janstenpickle.trace4cats.hotswap.ConditionalHotswapRefConstructor
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch

trait HotswapSpanExporter[F[_], G[_], A] extends SpanExporter[F, G] {
  def update(config: A): F[Boolean]
  def getConfig: F[A]
}

object HotswapSpanExporter {
  def apply[F[_]: Temporal, G[_], A: Eq](
    initialConfig: A
  )(makeExporter: A => Resource[F, SpanExporter[F, G]]): Resource[F, HotswapSpanExporter[F, G, A]] =
    ConditionalHotswapRefConstructor[F, A, SpanExporter[F, G]](initialConfig)(makeExporter).map { hotswap =>
      new HotswapSpanExporter[F, G, A] {
        override def update(config: A): F[Boolean] = hotswap.maybeSwapWith(config)
        override def getConfig: F[A] = hotswap.accessI.use(_.pure)
        override def exportBatch(batch: Batch[G]): F[Unit] = hotswap.accessR.use(_.exportBatch(batch))
      }
    }
}
