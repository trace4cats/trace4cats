package io.janstenpickle.trace4cats.http4s.server

import cats.data.{Kleisli, OptionT}
import cats.effect.Bracket
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{~>, Applicative}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.http4s.common.{Http4sHeaders, Http4sStatusMapping}
import io.janstenpickle.trace4cats.inject.EntryPoint
import io.janstenpickle.trace4cats.model.SpanKind
import org.http4s.{HttpRoutes, Request, Response}

trait RoutesSyntax {
  implicit class TracedRoutes[F[_]](routes: HttpRoutes[Kleisli[F, Span[F], *]]) {
    def inject(entryPoint: EntryPoint[F])(implicit F: Bracket[F, Throwable]): HttpRoutes[F] =
      Kleisli[OptionT[F, *], Request[F], Response[F]] { req =>
        type G[A] = Kleisli[F, Span[F], A]
        val lift = λ[F ~> G](fa => Kleisli(_ => fa))
        val headers = req.headers.toList.map(h => h.name.value -> h.value).toMap
        val spanR = entryPoint.continueOrElseRoot(req.uri.path, SpanKind.Server, headers)
        OptionT[F, Response[F]] {
          spanR.use { span =>
            val lower = λ[G ~> F](_(span))
            span.putAll(Http4sHeaders.requestFields(req): _*) *> routes
              .run(req.mapK(lift))
              .mapK(lower)
              .map(_.mapK(lower))
              .value
              .flatMap {
                case Some(resp) =>
                  span.setStatus(Http4sStatusMapping.toSpanStatus(resp.status)) *>
                    span
                      .putAll(Http4sHeaders.responseFields(resp): _*)
                      .as(Some(resp))
                case None => Applicative[F].pure(None)
              }
          }
        }
      }
  }
}
