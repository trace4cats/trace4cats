package trace4cats.dynamic.config

import cats.effect.kernel.{Resource, Temporal}
import trace4cats.dynamic.PollingSpanSampler
import trace4cats.kernel.SpanSampler
import trace4cats.model.{SampleDecision, SpanContext, SpanKind, TraceId}

import scala.concurrent.duration.FiniteDuration

object ConfiguredPollingSpanSampler {
  def apply[F[_]: Temporal](config: F[SamplerConfig], updateInterval: FiniteDuration): Resource[F, SpanSampler[F]] =
    PollingSpanSampler(config, updateInterval)(makeSampler[F])
      .map(underlying =>
        new SpanSampler[F] {
          override def shouldSample(
            parentContext: Option[SpanContext],
            traceId: TraceId,
            spanName: String,
            spanKind: SpanKind
          ): F[SampleDecision] = underlying.shouldSample(parentContext, traceId, spanName, spanKind)
        }
      )
}
