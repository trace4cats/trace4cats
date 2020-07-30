package io.janstenpickle.trace4cats.http4s.client

import cats.data.Kleisli
import cats.effect.Sync
import cats.syntax.functor._
import cats.~>
import io.janstenpickle.trace4cats.http4s.common.{Http4sHeaders, Http4sStatusMapping}
import io.janstenpickle.trace4cats.inject.{EntryPoint, Trace}
import io.janstenpickle.trace4cats.model.SpanKind
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import org.http4s.client.Client
import org.http4s.{HttpApp, Request, Response}

trait ClientSyntax {
  implicit class TracedClient[F[_]](client: Client[F]) {
    def inject(entryPoint: EntryPoint[F], toHeaders: ToHeaders = ToHeaders.w3c)(
      implicit F: Sync[F]
    ): Client[Kleisli[F, Span[F], *]] = {
      type G[A] = Kleisli[F, Span[F], A]
      val trace = Trace[G]
      val lift = λ[F ~> G](fa => Kleisli(_ => fa))
      val responseToTrace: Response[F] => Response[G] = resp => resp.mapK(lift)
      val traceToClientRequest: Request[G] => Request[F] =
        req => {
          val headers = Http4sHeaders.reqHeaders(req)
          val spanR = entryPoint.continueOrElseRoot(req.uri.path, SpanKind.Client, headers)
          val lower = λ[G ~> F](x => spanR.use(x.run))
          req.mapK(lower)
        }

      def contextHttpApp(app: HttpApp[F]): Kleisli[G, Request[G], Response[G]] =
        Kleisli[G, Request[G], Response[G]] { request =>
          trace.headers(toHeaders).flatMap { headers =>
            val req = request.putHeaders(Http4sHeaders.traceHeadersToHttp(headers): _*)

            app
              .mapK(lift)
              .map(responseToTrace)
              .flatMapF { resp =>
                trace.setStatus(Http4sStatusMapping.toSpanStatus(resp.status)).as(resp)
              }
              .run(traceToClientRequest(req))
          }
        }

      Client.fromHttpApp[G](contextHttpApp(client.toHttpApp))
    }

  }
}
