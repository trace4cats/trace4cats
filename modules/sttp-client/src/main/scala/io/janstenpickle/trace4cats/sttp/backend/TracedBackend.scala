package io.janstenpickle.trace4cats.sttp.backend

import cats.data.Kleisli
import cats.effect.Bracket
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import sttp.client.SttpBackend

case class TracedBackend[F[_]: Bracket[*[_], Throwable], -S, -WS_HANDLER[_]](
  backend: SttpBackend[F, S, WS_HANDLER],
  toHeaders: ToHeaders = ToHeaders.all,
  spanNamer: SttpSpanNamer = SttpSpanNamer.methodWithPath
) extends BackendTracer[F, Kleisli[F, Span[F], *], S, WS_HANDLER](
      backend,
      Kleisli.liftK[F, Span[F]],
      toHeaders,
      spanNamer
    )
