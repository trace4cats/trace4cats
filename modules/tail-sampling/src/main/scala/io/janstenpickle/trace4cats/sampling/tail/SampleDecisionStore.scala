package io.janstenpickle.trace4cats.sampling.tail

import io.janstenpickle.trace4cats.model.{SampleDecision, TraceId}

trait SampleDecisionStore[F[_]] {
  def getDecision(traceId: TraceId): F[Option[SampleDecision]]
  def batch(traceIds: Set[TraceId]): F[Map[TraceId, SampleDecision]]
  def storeDecision(traceId: TraceId, sampleDecision: SampleDecision): F[Unit]
  def storeDecisions(decisions: Map[TraceId, SampleDecision]): F[Unit]
}

object SampleDecisionStore {
  def apply[F[_]](implicit store: SampleDecisionStore[F]): SampleDecisionStore[F] = store
}
