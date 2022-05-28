import cats.data.Kleisli
import cats.effect.kernel.Resource
import trace4cats.{kernel => t4ckernel, model => t4cmodel}

import scala.annotation.unchecked.uncheckedVariance

package object trace4cats {
  type SpanName = String
  type SpanParams = (SpanName, SpanKind, TraceHeaders, ErrorHandler)
  type ResourceKleisli[F[_], -In, +Out] = Kleisli[Resource[F, +*], In, Out @uncheckedVariance]

  // kernel aliases

  type ErrorHandler = t4ckernel.ErrorHandler
  val ErrorHandler = t4ckernel.ErrorHandler

  type Span[F[_]] = t4ckernel.Span[F]
  val Span = t4ckernel.Span

  type SpanCompleter[F[_]] = t4ckernel.SpanCompleter[F]
  val SpanCompleter = t4ckernel.SpanCompleter

  type SpanExporter[F[_], G[_]] = t4ckernel.SpanExporter[F, G]
  val SpanExporter = t4ckernel.SpanExporter

  type SpanSampler[F[_]] = t4ckernel.SpanSampler[F]
  val SpanSampler = t4ckernel.SpanSampler

  type ToHeaders = t4ckernel.ToHeaders
  val ToHeaders = t4ckernel.ToHeaders

  // model aliases

  type AttributeValue = t4cmodel.AttributeValue
  val AttributeValue = t4cmodel.AttributeValue

  type Batch[F[_]] = t4cmodel.Batch[F]
  val Batch = t4cmodel.Batch

  type CompletedSpan = t4cmodel.CompletedSpan
  val CompletedSpan = t4cmodel.CompletedSpan

  type SampleDecision = t4cmodel.SampleDecision
  val SampleDecision = t4cmodel.SampleDecision

  type SpanContext = t4cmodel.SpanContext
  val SpanContext = t4cmodel.SpanContext

  type SpanId = t4cmodel.SpanId
  val SpanId = t4cmodel.SpanId

  type SpanKind = t4cmodel.SpanKind
  val SpanKind = t4cmodel.SpanKind

  type SpanStatus = t4cmodel.SpanStatus
  val SpanStatus = t4cmodel.SpanStatus

  type TraceHeaders = t4cmodel.TraceHeaders
  val TraceHeaders = t4cmodel.TraceHeaders

  type TraceId = t4cmodel.TraceId
  val TraceId = t4cmodel.TraceId

  type TraceProcess = t4cmodel.TraceProcess
  val TraceProcess = t4cmodel.TraceProcess

  type TraceProcessBuilder[F[_]] = t4cmodel.TraceProcessBuilder[F]
  val TraceProcessBuilder = t4cmodel.TraceProcessBuilder

  type TraceState = t4cmodel.TraceState
  val TraceState = t4cmodel.TraceState
}
