package io.janstenpickle.trace4cats.`export`

import cats.Applicative
import cats.effect.Ref
import cats.effect.kernel.{Concurrent, Resource}
import cats.effect.std.Hotswap
import cats.kernel.Eq
import cats.syntax.eq._
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.model.Batch

trait HotswapSpanExporter[F[_], G[_], A] extends SpanExporter[F, G] {
  def update(id: A, exporterResource: Resource[F, SpanExporter[F, G]]): F[Boolean]
}

object HotswapSpanExporter {
  def apply[F[_]: Concurrent, G[_], A: Eq](
    initialId: A,
    initialExporter: Resource[F, SpanExporter[F, G]]
  ): Resource[F, HotswapSpanExporter[F, G, A]] =
    for {
      (hotswap, exporter) <- Hotswap(initialExporter)
      current <- Resource.eval(Ref.of(exporter -> initialId))
    } yield new HotswapSpanExporter[F, G, A] {
      override def update(newId: A, exporterResource: Resource[F, SpanExporter[F, G]]): F[Boolean] =
        current.get.map(_._2.neqv(newId)).flatTap { idChanged =>
          Applicative[F]
            .whenA(idChanged)(hotswap.swap(exporterResource.evalTap(newExporter => current.set((newExporter, newId)))))
        }

      override def exportBatch(batch: Batch[G]): F[Unit] = current.get.flatMap(_._1.exportBatch(batch))
    }
}
