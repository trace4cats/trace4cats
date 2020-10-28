package io.janstenpickle.trace4cats.sttp.backend

import cats.MonadError
import cats.effect.Bracket
import cats.mtl.Ask
import cats.syntax.flatMap._
import io.janstenpickle.trace4cats.inject.LiftTrace
import io.janstenpickle.trace4cats.model.SpanKind
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import sttp.client.impl.cats.CatsMonadError
import sttp.client.monad.{MonadError => SttpMonadError}
import sttp.client.ws.WebSocketResponse
import sttp.client.{HttpError, Request, Response, SttpBackend}

class BackendTracer[F[_]: Bracket[*[_], Throwable], G[_]: MonadError[*[_], Throwable], -S, -WS_HANLDER[_]](
  backend: SttpBackend[F, S, WS_HANLDER],
  toHeaders: ToHeaders,
  spanNamer: SttpSpanNamer
)(implicit ask: Ask[G, Span[F]], liftTrace: LiftTrace[F, G])
    extends SttpBackend[G, S, WS_HANLDER] {
  override def send[T](request: Request[T, S]): G[Response[T]] =
    ask
      .ask[Span[F]]
      .flatMap { parent =>
        liftTrace(
          parent
            .child(spanNamer(request), SpanKind.Client, {
              case err @ HttpError(_, _) => SttpStatusMapping.errorToSpanStatus(err)
            })
            .use { span =>
              val headers = toHeaders.fromContext(span.context)
              val req = request.headers(headers)

              backend.send(req).flatTap { resp =>
                span.setStatus(SttpStatusMapping.statusToSpanStatus(resp.statusText, resp.code))
              }

            }
        )
      }

  override def openWebsocket[T, WS_RESULT](
    request: Request[T, S],
    handler: WS_HANLDER[WS_RESULT]
  ): G[WebSocketResponse[WS_RESULT]] = liftTrace(backend.openWebsocket(request, handler))

  override def close(): G[Unit] = liftTrace(backend.close())

  override def responseMonad: SttpMonadError[G] = new CatsMonadError[G]()
}
