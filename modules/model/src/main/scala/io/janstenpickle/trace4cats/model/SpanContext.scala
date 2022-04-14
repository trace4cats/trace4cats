package io.janstenpickle.trace4cats.model

import cats.syntax.all._
import cats.{Apply, Eq, Functor, Show}

case class SpanContext(
  traceId: TraceId,
  spanId: SpanId,
  parent: Option[Parent],
  traceFlags: TraceFlags,
  traceState: TraceState,
  isRemote: Boolean
) {
  def setDrop(): SpanContext =
    copy(traceFlags = traceFlags.copy(sampled = SampleDecision.Drop))
}

object SpanContext {
  def root[F[_]: Apply: TraceId.Gen: SpanId.Gen]: F[SpanContext] =
    (TraceId.gen[F], SpanId.gen[F])
      .mapN(SpanContext(_, _, None, TraceFlags(SampleDecision.Include), TraceState.empty, isRemote = false))

  def child[F[_]: Functor: SpanId.Gen](parent: SpanContext, isRemote: Boolean = false): F[SpanContext] =
    SpanId.gen[F].map { spanId =>
      SpanContext(
        parent.traceId,
        spanId,
        Some(Parent(parent.spanId, parent.isRemote)),
        parent.traceFlags,
        parent.traceState,
        isRemote
      )
    }

  val invalid: SpanContext =
    SpanContext(
      TraceId.invalid,
      SpanId.invalid,
      None,
      TraceFlags(SampleDecision.Drop),
      TraceState.empty,
      isRemote = false
    )

  implicit val show: Show[SpanContext] = Show.show { c =>
    val parent = c.parent.fold("")(p => show", parent-id: ${p.spanId}")
    val state = if (c.traceState.values.isEmpty) "" else show", state: ${c.traceState}"

    show"[ trace-id: ${c.traceId}, span-id: ${c.spanId}$parent$state, sampled: ${c.traceFlags.sampled}, remote: ${c.isRemote} ]"
  }

  implicit val eq: Eq[SpanContext] =
    Eq.by(sc => (sc.traceId, sc.spanId, sc.parent, sc.traceFlags, sc.traceState, sc.isRemote))
}
