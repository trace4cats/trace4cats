package io.janstenpickle.trace4cats.sttp.client

import cats.syntax.flatMap._
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.base.optics.{Getter, Lens}
import io.janstenpickle.trace4cats.model.{SpanKind, TraceHeaders}
import sttp.client.impl.cats.CatsMonadError
import sttp.client.monad.{MonadError => SttpMonadError}
import sttp.client.ws.WebSocketResponse
import sttp.client.{HttpError, Request, Response, SttpBackend}
import cats.MonadThrow
import cats.effect.MonadCancelThrow

class BackendTracer[F[_], G[_], -S, -WS_HANLDER[_], Ctx](
  backend: SttpBackend[F, S, WS_HANLDER],
  spanLens: Lens[Ctx, Span[F]],
  headersGetter: Getter[Ctx, TraceHeaders],
  spanNamer: SttpSpanNamer
)(implicit P: Provide[F, G, Ctx], F: MonadCancelThrow[F], G: MonadThrow[G])
    extends SttpBackend[G, S, WS_HANLDER] {
  override def send[T](request: Request[T, S]): G[Response[T]] =
    P.kleislift { parentCtx =>
      val parentSpan = spanLens.get(parentCtx)
      parentSpan
        .child(
          spanNamer(request),
          SpanKind.Client,
          { case err @ HttpError(_, _) =>
            SttpStatusMapping.errorToSpanStatus(err)
          }
        )
        .use { childSpan =>
          val childCtx = spanLens.set(childSpan)(parentCtx)
          val headers = headersGetter.get(childCtx)
          val req = request.headers(SttpHeaders.converter.to(headers).headers: _*)

          backend.send(req).flatTap { resp =>
            childSpan.setStatus(SttpStatusMapping.statusToSpanStatus(resp.statusText, resp.code))
          }
        }
    }

  override def openWebsocket[T, WS_RESULT](
    request: Request[T, S],
    handler: WS_HANLDER[WS_RESULT]
  ): G[WebSocketResponse[WS_RESULT]] = P.lift(backend.openWebsocket(request, handler))

  override def close(): G[Unit] = P.lift(backend.close())

  override def responseMonad: SttpMonadError[G] = new CatsMonadError[G]()
}
