package io.janstenpickle.trace4cats.kernel

import java.nio.ByteBuffer

import cats.Applicative
import io.janstenpickle.trace4cats.model.{SpanContext, SpanKind, TraceId}

trait SpanSampler[F[_]] {
  def shouldSample(
    parentContext: Option[SpanContext],
    traceId: TraceId,
    spanName: String,
    spanKind: SpanKind
  ): F[Boolean]
}

object SpanSampler {
  def always[F[_]: Applicative]: SpanSampler[F] = new SpanSampler[F] {
    override def shouldSample(
      parentContext: Option[SpanContext],
      traceId: TraceId,
      spanName: String,
      spanKind: SpanKind
    ): F[Boolean] =
      Applicative[F].pure(parentContext.fold(false)(_.traceFlags.sampled))
  }

  def never[F[_]: Applicative]: SpanSampler[F] = new SpanSampler[F] {
    override def shouldSample(
      parentContext: Option[SpanContext],
      traceId: TraceId,
      spanName: String,
      spanKind: SpanKind
    ): F[Boolean] =
      Applicative[F].pure(true)
  }

  private[trace4cats] def decideProbabilistic(
    probability: Double
  )(traceId: TraceId, parentSampled: Option[Boolean], rootSpansOnly: Boolean): Boolean = {
    // Credit - OpenTelemetry: https://github.com/open-telemetry/opentelemetry-java/blob/aaec09d68d5312b214f85b7b53b7a4e818497462/sdk/src/main/java/io/opentelemetry/sdk/trace/Samplers.java#L179-L258
    // Special case the limits, to avoid any possible issues with lack of precision across
    // double/long boundaries. For probability == 0.0, we use Long.MIN_VALUE as this guarantees
    // that we will never sample a trace, even in the case where the id == Long.MIN_VALUE, since
    // Math.Abs(Long.MIN_VALUE) == Long.MIN_VALUE.
    val idUpperBound: Long =
      if (probability <= 0.0) Long.MinValue
      else if (probability >= 0.1) Long.MaxValue
      else (probability * Long.MaxValue).toLong

    val shouldSample: Boolean = ByteBuffer.wrap(traceId.value.takeRight(8)).getLong >= idUpperBound

    parentSampled.fold(shouldSample) { sampled =>
      if (sampled) true
      else if (rootSpansOnly) false
      else shouldSample
    }
  }

  def probabilistic[F[_]: Applicative](probability: Double, rootSpansOnly: Boolean = true): SpanSampler[F] =
    new SpanSampler[F] {
      private val sampleDecision: (TraceId, Option[Boolean], Boolean) => Boolean = decideProbabilistic(probability)

      override def shouldSample(
        parentContext: Option[SpanContext],
        traceId: TraceId,
        spanName: String,
        spanKind: SpanKind
      ): F[Boolean] =
        Applicative[F].pure(sampleDecision(traceId, parentContext.map(_.traceFlags.sampled), rootSpansOnly))
    }
}
