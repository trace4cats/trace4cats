import trace4cats.{kernel => t4ckernel}

package object trace4cats {
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
