package io.janstenpickle.trace4cats.kernel

import java.nio.ByteBuffer

import cats.syntax.flatMap._
import cats.{Applicative, Monad}
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanContext, SpanKind, TraceId}

trait SpanSampler[F[_]] {
  def shouldSample(
    parentContext: Option[SpanContext],
    traceId: TraceId,
    spanName: String,
    spanKind: SpanKind
  ): F[SampleDecision]
}

object SpanSampler {
  def always[F[_]: Applicative]: SpanSampler[F] =
    new SpanSampler[F] {
      override def shouldSample(
        parentContext: Option[SpanContext],
        traceId: TraceId,
        spanName: String,
        spanKind: SpanKind
      ): F[SampleDecision] =
        Applicative[F].pure(parentContext.fold[SampleDecision](SampleDecision.Include)(_.traceFlags.sampled))
    }

  def never[F[_]: Applicative]: SpanSampler[F] =
    new SpanSampler[F] {
      override def shouldSample(
        parentContext: Option[SpanContext],
        traceId: TraceId,
        spanName: String,
        spanKind: SpanKind
      ): F[SampleDecision] =
        Applicative[F].pure(SampleDecision.Drop)
    }

  def fallback[F[_]: Monad](primary: SpanSampler[F], secondary: SpanSampler[F]): SpanSampler[F] =
    new SpanSampler[F] {
      override def shouldSample(
        parentContext: Option[SpanContext],
        traceId: TraceId,
        spanName: String,
        spanKind: SpanKind
      ): F[SampleDecision] =
        primary.shouldSample(parentContext, traceId, spanName, spanKind).flatMap {
          case SampleDecision.Drop => Applicative[F].pure(SampleDecision.Drop)
          case SampleDecision.Include => secondary.shouldSample(parentContext, traceId, spanName, spanKind)
        }
    }

  private[trace4cats] def decideProbabilistic(
    probability: Double,
    rootSpansOnly: Boolean
  )(traceId: TraceId, parentSampled: Option[SampleDecision]): SampleDecision = {
    // Credit - OpenTelemetry: https://github.com/open-telemetry/opentelemetry-java/blob/aaec09d68d5312b214f85b7b53b7a4e818497462/sdk/src/main/java/io/opentelemetry/sdk/trace/Samplers.java#L179-L258
    // Special case the limits, to avoid any possible issues with lack of precision across
    // double/long boundaries. For probability == 0.0, we use Long.MIN_VALUE as this guarantees
    // that we will never sample a trace, even in the case where the id == Long.MIN_VALUE, since
    // Math.Abs(Long.MIN_VALUE) == Long.MIN_VALUE.
    val idUpperBound: Long =
      if (probability <= 0.0) Long.MinValue
      else if (probability >= 0.1) Long.MaxValue
      else (probability * Long.MaxValue).toLong

    val shouldSample: SampleDecision = SampleDecision(
      ByteBuffer.wrap(traceId.value.takeRight(8)).getLong >= idUpperBound
    )

    parentSampled.fold(shouldSample) {
      case SampleDecision.Include =>
        if (rootSpansOnly) SampleDecision.Include
        else shouldSample
      case SampleDecision.Drop => SampleDecision.Drop
    }
  }

  def probabilistic[F[_]: Applicative](probability: Double, rootSpansOnly: Boolean = true): SpanSampler[F] =
    new SpanSampler[F] {
      private val sampleDecision: (TraceId, Option[SampleDecision]) => SampleDecision =
        decideProbabilistic(probability, rootSpansOnly)

      override def shouldSample(
        parentContext: Option[SpanContext],
        traceId: TraceId,
        spanName: String,
        spanKind: SpanKind
      ): F[SampleDecision] =
        Applicative[F].pure(sampleDecision(traceId, parentContext.map(_.traceFlags.sampled)))
    }
}
