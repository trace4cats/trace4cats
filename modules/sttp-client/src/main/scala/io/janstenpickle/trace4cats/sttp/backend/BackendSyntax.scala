package io.janstenpickle.trace4cats.sttp.backend

import cats.data.Kleisli
import cats.effect.Bracket
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import sttp.client.SttpBackend

trait BackendSyntax {

  implicit class TracedBackendSyntax[F[_], -S, -WS_HANDLER[_]](backend: SttpBackend[F, S, WS_HANDLER]) {
    def liftTrace(toHeaders: ToHeaders = ToHeaders.all, spanNamer: SttpSpanNamer = SttpSpanNamer.methodWithPath)(
      implicit F: Bracket[F, Throwable]
    ): SttpBackend[Kleisli[F, Span[F], *], S, WS_HANDLER] =
      TracedBackend(backend, toHeaders, spanNamer)
  }

}
