import cats.data.Kleisli
import cats.effect.kernel.Resource
import trace4cats.model.{SpanKind, TraceHeaders}
import trace4cats.{kernel => t4ckernel}

import scala.annotation.unchecked.uncheckedVariance

package object trace4cats {
  type SpanName = String
  type SpanParams = (SpanName, SpanKind, TraceHeaders, ErrorHandler)
  type ResourceKleisli[F[_], -In, +Out] = Kleisli[Resource[F, +*], In, Out @uncheckedVariance]

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
}
