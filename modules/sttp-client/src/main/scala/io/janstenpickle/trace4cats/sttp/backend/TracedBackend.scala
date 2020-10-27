package io.janstenpickle.trace4cats.sttp.backend

import cats.MonadError
import cats.effect.Bracket
import cats.mtl.Ask
import io.janstenpickle.trace4cats.inject.LiftTrace
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import sttp.client.SttpBackend

case class TracedBackend[F[_]: Bracket[*[_], Throwable], G[_]: MonadError[*[_], Throwable], -S, -WS_HANDLER[_]](
  backend: SttpBackend[F, S, WS_HANDLER],
  toHeaders: ToHeaders = ToHeaders.all,
  spanNamer: SttpSpanNamer = SttpSpanNamer.methodWithPath
)(implicit ask: Ask[G, Span[F]], liftTrace: LiftTrace[F, G])
    extends BackendTracer[F, G, S, WS_HANDLER](backend, toHeaders, spanNamer)
