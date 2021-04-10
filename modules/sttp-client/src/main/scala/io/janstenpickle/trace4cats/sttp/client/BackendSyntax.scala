package io.janstenpickle.trace4cats.sttp.client

import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.base.optics.{Getter, Lens}
import io.janstenpickle.trace4cats.model.TraceHeaders
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import sttp.client.SttpBackend
import cats.MonadThrow
import cats.effect.MonadCancelThrow

trait BackendSyntax {

  implicit class TracedBackendSyntax[F[_], S, WS_HANDLER[_]](backend: SttpBackend[F, S, WS_HANDLER]) {
    def liftTrace[G[_]](
      toHeaders: ToHeaders = ToHeaders.all,
      spanNamer: SttpSpanNamer = SttpSpanNamer.methodWithPath
    )(implicit P: Provide[F, G, Span[F]], F: MonadCancelThrow[F], G: MonadThrow[G]): SttpBackend[G, S, WS_HANDLER] =
      new BackendTracer[F, G, S, WS_HANDLER, Span[F]](
        backend,
        Lens.id,
        Getter((toHeaders.fromContext _).compose(_.context)),
        spanNamer
      )

    def liftTraceContext[G[_], Ctx](
      spanLens: Lens[Ctx, Span[F]],
      headersGetter: Getter[Ctx, TraceHeaders],
      spanNamer: SttpSpanNamer = SttpSpanNamer.methodWithPath
    )(implicit P: Provide[F, G, Ctx], F: MonadCancelThrow[F], G: MonadThrow[G]): SttpBackend[G, S, WS_HANDLER] =
      new BackendTracer[F, G, S, WS_HANDLER, Ctx](backend, spanLens, headersGetter, spanNamer)
  }

}
