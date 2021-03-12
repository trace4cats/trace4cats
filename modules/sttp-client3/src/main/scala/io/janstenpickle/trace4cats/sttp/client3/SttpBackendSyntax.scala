package io.janstenpickle.trace4cats.sttp.client3

import cats.effect.kernel.{Async, MonadCancelThrow}
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.base.optics.{Getter, Lens}
import io.janstenpickle.trace4cats.model.TraceHeaders
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import sttp.client3.SttpBackend

trait SttpBackendSyntax {

  implicit class TracedSttpBackendSyntax[F[_], +P](backend: SttpBackend[F, P]) {
    def liftTrace[G[_]](
      toHeaders: ToHeaders = ToHeaders.all,
      spanNamer: SttpSpanNamer = SttpSpanNamer.methodWithPath
    )(implicit P: Provide[F, G, Span[F]], F: MonadCancelThrow[F], G: Async[G]): SttpBackend[G, P] =
      new SttpBackendTracer[F, G, P, Span[F]](
        backend,
        Lens.id,
        Getter((toHeaders.fromContext _).compose(_.context)),
        spanNamer
      )

    def liftTraceContext[G[_], Ctx](
      spanLens: Lens[Ctx, Span[F]],
      headersGetter: Getter[Ctx, TraceHeaders],
      spanNamer: SttpSpanNamer = SttpSpanNamer.methodWithPath
    )(implicit P: Provide[F, G, Ctx], F: MonadCancelThrow[F], G: Async[G]): SttpBackend[G, P] =
      new SttpBackendTracer[F, G, P, Ctx](backend, spanLens, headersGetter, spanNamer)
  }

}
