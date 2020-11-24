package io.janstenpickle.trace4cats.http4s.client

import cats.effect.{Bracket, Resource}
import cats.{Applicative, Defer}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.http4s.common.{Http4sHeaders, Http4sSpanNamer, Http4sStatusMapping, Request_}
import io.janstenpickle.trace4cats.inject.UnliftProvide
import io.janstenpickle.trace4cats.model.{SpanKind, TraceHeaders}
import monocle.{Getter, Lens}
import org.http4s.Request
import org.http4s.client.{Client, UnexpectedStatus}

object ClientTracer {
  def liftTrace[F[_]: Applicative, G[_]: Applicative: Defer: Bracket[*[_], Throwable], Ctx](
    client: Client[F],
    spanLens: Lens[Ctx, Span[F]],
    headersGetter: Getter[Ctx, TraceHeaders],
    spanNamer: Http4sSpanNamer
  )(implicit UP: UnliftProvide[F, G, Ctx]): Client[G] =
    Client { request: Request[G] =>
      Resource
        .liftF(UP.ask[Ctx])
        .flatMap { parentCtx =>
          val parentSpan = spanLens.get(parentCtx)
          parentSpan
            .child(
              spanNamer(request),
              SpanKind.Client,
              { case UnexpectedStatus(status) =>
                Http4sStatusMapping.toSpanStatus(status)
              }
            )
            .flatMap { childSpan =>
              val childCtx = spanLens.set(childSpan)(parentCtx)
              val headers = headersGetter.get(childCtx)
              val req = request.putHeaders(Http4sHeaders.converter.to(headers).toList: _*)

              client
                .run(req.mapK(UP.provideK(childCtx)))
                .evalTap { resp =>
                  childSpan.setStatus(Http4sStatusMapping.toSpanStatus(resp.status))
                }
            }
            .mapK(UP.liftK)
            .map(_.mapK(UP.liftK))
        }
    }
}
