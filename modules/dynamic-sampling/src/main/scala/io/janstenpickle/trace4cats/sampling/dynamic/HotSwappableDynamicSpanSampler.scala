package io.janstenpickle.trace4cats.sampling.dynamic

import cats.Applicative
import cats.effect.kernel.{Ref, Resource, Temporal}
import cats.effect.std.Hotswap
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanContext, SpanKind, TraceId}

trait HotSwappableDynamicSpanSampler[F[_], A] extends SpanSampler[F] {
  def updateSampler(id: A, samplerResource: Resource[F, SpanSampler[F]]): F[Boolean]
  def getId: F[A]
}

object HotSwappableDynamicSpanSampler {
  def create[F[_]: Temporal, A](
    id: A,
    initial: Resource[F, SpanSampler[F]]
  ): Resource[F, HotSwappableDynamicSpanSampler[F, A]] = for {
    (hotswap, sampler) <- Hotswap(initial)
    current <- Resource.eval(Ref.of((sampler, id)))
  } yield new HotSwappableDynamicSpanSampler[F, A] {
    def updateSampler(newId: A, samplerResource: Resource[F, SpanSampler[F]]): F[Boolean] =
      current.get
        .map(_._2 != newId)
        .flatTap { idChanged =>
          Applicative[F].whenA(idChanged)(
            hotswap.swap(samplerResource.evalTap(newSampler => current.set((newSampler, newId))))
          )
        }

    override def getId: F[A] = current.get.map(_._2)

    override def shouldSample(
      parentContext: Option[SpanContext],
      traceId: TraceId,
      spanName: String,
      spanKind: SpanKind
    ): F[SampleDecision] = current.get.flatMap { case (sampler, _) =>
      sampler.shouldSample(parentContext, traceId, spanName, spanKind)
    }

  }
}
