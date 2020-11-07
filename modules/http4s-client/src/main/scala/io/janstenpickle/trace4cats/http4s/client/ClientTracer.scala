package io.janstenpickle.trace4cats.http4s.client

import cats.effect.{Bracket, Resource}
import cats.mtl.Ask
import cats.{Applicative, Defer}
import io.janstenpickle.trace4cats.http4s.common.{Http4sHeaders, Http4sSpanNamer, Http4sStatusMapping}
import io.janstenpickle.trace4cats.inject.{LiftTrace, Provide}
import io.janstenpickle.trace4cats.model.SpanKind
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import org.http4s.Request
import org.http4s.client.{Client, UnexpectedStatus}

object ClientTracer {
  def liftTrace[F[_]: Applicative, G[_]: Applicative: Defer: Bracket[*[_], Throwable]](
    client: Client[F],
    toHeaders: ToHeaders,
    spanNamer: Http4sSpanNamer
  )(implicit ask: Ask[G, Span[F]], provide: Provide[F, G], lift: LiftTrace[F, G]): Client[G] =
    Client { request: Request[G] =>
      Resource
        .liftF(ask.ask[Span[F]])
        .flatMap(
          _.child(
            spanNamer(request.covary),
            SpanKind.Client,
            {
              case UnexpectedStatus(status) => Http4sStatusMapping.toSpanStatus(status)
            }
          ).flatMap { span =>
              val headers = toHeaders.fromContext(span.context)
              val req = request.putHeaders(Http4sHeaders.traceHeadersToHttp(headers): _*)

              client
                .run(req.mapK(provide.fk(span)))
                .evalTap { resp =>
                  span.setStatus(Http4sStatusMapping.toSpanStatus(resp.status))
                }
            }
            .mapK(lift.fk)
            .map(_.mapK(lift.fk))
        )
    }
}
