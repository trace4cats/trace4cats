package trace4cats

import cats.Applicative
import cats.effect.kernel.{Resource, Temporal}
import cats.syntax.functor._
import trace4cats.model.{SampleDecision, SpanContext, SpanKind, TraceId}
import trace4cats.rate.{TokenBucket, TokenInterval}

import scala.concurrent.duration._

object RateSpanSampler {
  def apply[F[_]: Applicative: TokenBucket]: SpanSampler[F] = apply(rootSpansOnly = true)

  def apply[F[_]: Applicative: TokenBucket](rootSpansOnly: Boolean): SpanSampler[F] =
    new SpanSampler[F] {
      override def shouldSample(
        parentContext: Option[SpanContext],
        traceId: TraceId,
        spanName: String,
        spanKind: SpanKind
      ): F[SampleDecision] = {
        val sampleDecisionF: F[SampleDecision] = TokenBucket[F].request1.map {
          case false => SampleDecision.Drop
          case true => SampleDecision.Include
        }

        parentContext.map(_.traceFlags.sampled).fold(sampleDecisionF) {
          case SampleDecision.Include =>
            if (rootSpansOnly) Applicative[F].pure(SampleDecision.Include)
            else sampleDecisionF
          case SampleDecision.Drop => Applicative[F].pure(SampleDecision.Drop)
        }
      }

    }

  def create[F[_]: Temporal](bucketSize: Int, tokenRate: Double): Resource[F, SpanSampler[F]] =
    create(bucketSize, tokenRate, rootSpansOnly = true)

  def create[F[_]: Temporal](bucketSize: Int, tokenRate: Double, rootSpansOnly: Boolean): Resource[F, SpanSampler[F]] =
    TokenInterval(tokenRate)
      .fold(Resource.pure[F, SpanSampler[F]](SpanSampler.always[F]))(create[F](bucketSize, _, rootSpansOnly))

  def create[F[_]: Temporal](bucketSize: Int, tokenInterval: FiniteDuration): Resource[F, SpanSampler[F]] =
    create(bucketSize, tokenInterval, rootSpansOnly = true)

  def create[F[_]: Temporal](
    bucketSize: Int,
    tokenInterval: FiniteDuration,
    rootSpansOnly: Boolean
  ): Resource[F, SpanSampler[F]] =
    TokenBucket.create[F](bucketSize, tokenInterval).map(implicit tb => apply[F](rootSpansOnly))
}
