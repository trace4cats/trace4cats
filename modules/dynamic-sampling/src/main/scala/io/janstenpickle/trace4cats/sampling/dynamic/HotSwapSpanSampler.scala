package io.janstenpickle.trace4cats.sampling.dynamic

import cats.Applicative
import cats.effect.kernel.{Ref, Resource, Temporal}
import cats.effect.std.Hotswap
import cats.kernel.Eq
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanContext, SpanKind, TraceId}
import cats.syntax.eq._

trait HotSwapSpanSampler[F[_], A] extends SpanSampler[F] {
  def updateSampler(id: A, samplerResource: Resource[F, SpanSampler[F]]): F[Boolean]
  def getId: F[A]
}

object HotSwapSpanSampler {
  def create[F[_]: Temporal, A: Eq](
    id: A,
    initial: Resource[F, SpanSampler[F]]
  ): Resource[F, HotSwapSpanSampler[F, A]] = for {
    (hotswap, sampler) <- Hotswap(initial)
    current <- Resource.eval(Ref.of((sampler, id)))
  } yield new HotSwapSpanSampler[F, A] {
    def updateSampler(newId: A, samplerResource: Resource[F, SpanSampler[F]]): F[Boolean] =
      current.get
        .map(_._2.neqv(newId))
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
