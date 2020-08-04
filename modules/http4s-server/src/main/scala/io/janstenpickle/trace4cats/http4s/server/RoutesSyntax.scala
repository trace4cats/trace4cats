package io.janstenpickle.trace4cats.http4s.server

import cats.data.{Kleisli, OptionT}
import cats.effect.Bracket
import cats.syntax.apply._
import cats.syntax.functor._
import cats.~>
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.http4s.common.{Http4sHeaders, Http4sSpanNamer, Http4sStatusMapping}
import io.janstenpickle.trace4cats.inject.EntryPoint
import io.janstenpickle.trace4cats.model.SpanKind
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Headers, HttpRoutes, Request, Response}

trait RoutesSyntax {
  implicit class TracedRoutes[F[_]](routes: HttpRoutes[Kleisli[F, Span[F], *]]) {
    def inject(
      entryPoint: EntryPoint[F],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit F: Bracket[F, Throwable]): HttpRoutes[F] =
      Kleisli[OptionT[F, *], Request[F], Response[F]] { req =>
        type G[A] = Kleisli[F, Span[F], A]
        val lift = λ[F ~> G](fa => Kleisli(_ => fa))
        val headers = req.headers.toList.map(h => h.name.value -> h.value).toMap
        val spanR = entryPoint.continueOrElseRoot(spanNamer(req.covary), SpanKind.Server, headers)
        OptionT[F, Response[F]] {
          spanR.use { span =>
            val lower = λ[G ~> F](_(span))
            span.putAll(Http4sHeaders.requestFields(req, dropHeadersWhen): _*) *> routes
              .run(req.mapK(lift))
              .mapK(lower)
              .map(_.mapK(lower))
              .semiflatMap { resp =>
                span.setStatus(Http4sStatusMapping.toSpanStatus(resp.status)) *>
                  span
                    .putAll(Http4sHeaders.responseFields(resp, dropHeadersWhen): _*)
                    .as(resp)
              }
              .value
          }
        }
      }
  }
}
