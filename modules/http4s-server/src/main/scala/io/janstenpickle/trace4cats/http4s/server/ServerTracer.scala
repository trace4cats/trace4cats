package io.janstenpickle.trace4cats.http4s.server

import cats.data.{Kleisli, OptionT}
import cats.effect.Bracket
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.http4s.common.{
  Http4sHeaders,
  Http4sRequestFilter,
  Http4sSpanNamer,
  Http4sStatusMapping,
  Request_,
  Response_
}
import io.janstenpickle.trace4cats.inject.{EntryPoint, Spanned, UnliftProvide}
import io.janstenpickle.trace4cats.model.SpanKind
import org.http4s.util.CaseInsensitiveString
import org.http4s.{HttpApp, HttpRoutes, Request, Response}

object ServerTracer {
  def injectRoutes[F[_], G[_], Ctx](
    routes: HttpRoutes[G],
    entryPoint: EntryPoint[F],
    spanNamer: Http4sSpanNamer,
    requestFilter: Http4sRequestFilter,
    dropHeadersWhen: CaseInsensitiveString => Boolean,
    makeContext: Request_ => Spanned[F, Ctx]
  )(implicit UP: UnliftProvide[F, G, Ctx], F: Bracket[F, Throwable]): HttpRoutes[F] =
    Kleisli[OptionT[F, *], Request[F], Response[F]] { req =>
      val filter = requestFilter.lift(req).getOrElse(true)
      val headers = Http4sHeaders.converter.from(req.headers)
      val spanR =
        if (filter) entryPoint.continueOrElseRoot(spanNamer(req), SpanKind.Server, headers) else Span.noop[F]

      OptionT[F, Response[F]] {
        spanR.use { span =>
          for {
            _ <- span.putAll(Http4sHeaders.requestFields(req, dropHeadersWhen): _*)
            ctx <- makeContext(req).run(span)
            lower = UP.provideK(ctx)
            resp <-
              routes
                .run(req.mapK(UP.liftK))
                .mapK(lower)
                .map(_.mapK(lower))
                .semiflatTap { res =>
                  span.setStatus(Http4sStatusMapping.toSpanStatus(res.status)) *>
                    span.putAll(Http4sHeaders.responseFields(res, dropHeadersWhen): _*)
                }
                .value
          } yield resp
        }
      }
    }

  def injectApp[F[_], G[_], Ctx](
    app: HttpApp[G],
    entryPoint: EntryPoint[F],
    spanNamer: Http4sSpanNamer,
    requestFilter: Http4sRequestFilter,
    dropHeadersWhen: CaseInsensitiveString => Boolean,
    makeContext: Request_ => Spanned[F, Ctx]
  )(implicit UP: UnliftProvide[F, G, Ctx], F: Bracket[F, Throwable]): HttpApp[F] =
    Kleisli[F, Request[F], Response[F]] { req =>
      val filter = requestFilter.lift(req).getOrElse(true)
      val headers = Http4sHeaders.converter.from(req.headers)
      val spanR =
        if (filter) entryPoint.continueOrElseRoot(spanNamer(req), SpanKind.Server, headers) else Span.noop[F]

      spanR.use { span =>
        for {
          _ <- span.putAll(Http4sHeaders.requestFields(req, dropHeadersWhen): _*)
          ctx <- makeContext(req).run(span)
          lower = UP.provideK(ctx)
          resp <- lower(app.run(req.mapK(UP.liftK))).map(_.mapK(lower))
          _ <- span.setStatus(Http4sStatusMapping.toSpanStatus(resp.status))
          _ <- span.putAll(Http4sHeaders.responseFields(resp, dropHeadersWhen): _*)
        } yield resp
      }
    }
}
