package io.janstenpickle.trace4cats.sttp.backend

import cats.effect.Bracket
import cats.mtl.Ask
import cats.syntax.flatMap._
import cats.{~>, MonadError}
import io.janstenpickle.trace4cats.model.SpanKind
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import sttp.client.impl.cats.CatsMonadError
import sttp.client.monad.{MonadError => SttpMonadError}
import sttp.client.ws.WebSocketResponse
import sttp.client.{HttpError, Request, Response, SttpBackend}

class BackendTracer[F[_]: Bracket[*[_], Throwable], G[_]: MonadError[*[_], Throwable], -S, -WS_HANLDER[_]](
  backend: SttpBackend[F, S, WS_HANLDER],
  lift: F ~> G,
  toHeaders: ToHeaders,
  spanNamer: SttpSpanNamer
)(implicit ask: Ask[G, Span[F]])
    extends SttpBackend[G, S, WS_HANLDER] {
  override def send[T](request: Request[T, S]): G[Response[T]] =
    ask
      .ask[Span[F]]
      .flatMap { parent =>
        lift(
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
  ): G[WebSocketResponse[WS_RESULT]] = lift(backend.openWebsocket(request, handler))

  override def close(): G[Unit] = lift(backend.close())

  override def responseMonad: SttpMonadError[G] = new CatsMonadError[G]()
}
