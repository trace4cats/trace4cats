package io.janstenpickle.trace4cats.http4s.client

import cats.data.Kleisli
import cats.effect.{Resource, Sync}
import io.janstenpickle.trace4cats.http4s.common.{Http4sHeaders, Http4sSpanNamer, Http4sStatusMapping}
import io.janstenpickle.trace4cats.model.SpanKind
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import org.http4s.Request
import org.http4s.client.{Client, UnexpectedStatus}

trait ClientSyntax {
  implicit class TracedClient[F[_]](client: Client[F]) {
    def liftTrace(toHeaders: ToHeaders = ToHeaders.w3c, spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath)(
      implicit F: Sync[F]
    ): Client[Kleisli[F, Span[F], *]] =
      Client { request: Request[Kleisli[F, Span[F], *]] =>
        Resource
          .liftF(Kleisli.ask[F, Span[F]])
          .flatMap(
            _.child(spanNamer(request.covary), SpanKind.Client, {
              case UnexpectedStatus(status) => Http4sStatusMapping.toSpanStatus(status)
            }).flatMap { span =>
                val headers = toHeaders.fromContext(span.context)
                val req = request.putHeaders(Http4sHeaders.traceHeadersToHttp(headers): _*)

                client
                  .run(req.mapK(Kleisli.applyK(span)))
                  .evalTap { resp =>
                    span.setStatus(Http4sStatusMapping.toSpanStatus(resp.status))
                  }
              }
              .mapK(Kleisli.liftK[F, Span[F]])
              .map(_.mapK(Kleisli.liftK))
          )
      }
  }
}
