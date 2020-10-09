package io.janstenpickle.trace4cats.http4s.server

import cats.data.{Kleisli, OptionT}
import cats.effect.Bracket
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{~>, Monad}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.http4s.common.{Http4sHeaders, Http4sSpanNamer, Http4sStatusMapping}
import io.janstenpickle.trace4cats.inject.EntryPoint
import io.janstenpickle.trace4cats.model.SpanKind
import org.http4s.util.CaseInsensitiveString
import org.http4s.{HttpApp, HttpRoutes, Request, Response}

object ServerTracer {
  def injectRoutes[F[_]: Bracket[*[_], Throwable], G[_]](
    routes: HttpRoutes[G],
    entryPoint: EntryPoint[F],
    lift: F ~> G,
    lower: Span[F] => G ~> F,
    spanNamer: Http4sSpanNamer,
    dropHeadersWhen: CaseInsensitiveString => Boolean
  ): HttpRoutes[F] = Kleisli[OptionT[F, *], Request[F], Response[F]] { req =>
    val headers = req.headers.toList.map(h => h.name.value -> h.value).toMap
    val spanR = entryPoint.continueOrElseRoot(spanNamer(req.covary), SpanKind.Server, headers)

    OptionT[F, Response[F]] {
      spanR.use { span =>
        val low = lower(span)

        span.putAll(Http4sHeaders.requestFields(req, dropHeadersWhen): _*) *> routes
          .run(req.mapK(lift))
          .mapK(low)
          .map(_.mapK(low))
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

  def injectApp[F[_]: Bracket[*[_], Throwable], G[_]: Monad](
    app: HttpApp[G],
    entryPoint: EntryPoint[F],
    lift: F ~> G,
    lower: Span[F] => G ~> F,
    spanNamer: Http4sSpanNamer,
    dropHeadersWhen: CaseInsensitiveString => Boolean
  ): HttpApp[F] = Kleisli[F, Request[F], Response[F]] { req =>
    val headers = req.headers.toList.map(h => h.name.value -> h.value).toMap
    val spanR = entryPoint.continueOrElseRoot(spanNamer(req.covary), SpanKind.Server, headers)
    spanR.use { span =>
      val low = lower(span)
      span.putAll(Http4sHeaders.requestFields(req, dropHeadersWhen): _*) *> low(
        app
          .run(req.mapK(lift))
          .map(_.mapK(low))
      ).flatMap { resp =>
        span.setStatus(Http4sStatusMapping.toSpanStatus(resp.status)) *>
          span
            .putAll(Http4sHeaders.responseFields(resp, dropHeadersWhen): _*)
            .as(resp)
      }
    }
  }
}
