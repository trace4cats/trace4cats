package io.janstenpickle.trace4cats.http4s.server

import cats.Monad
import cats.data.{Kleisli, OptionT}
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Init
import io.janstenpickle.trace4cats.base.optics.Getter
import io.janstenpickle.trace4cats.http4s.common.{Http4sHeaders, Http4sStatusMapping, Request_, Response_}
import org.http4s.util.CaseInsensitiveString
import org.http4s.{HttpApp, HttpRoutes, Request, Response}

object ServerTracer {
  def injectRoutes[F[_], G[_]: Monad, Ctx](
    routes: HttpRoutes[G],
    dropHeadersWhen: CaseInsensitiveString => Boolean,
    getter: Getter[Ctx, Span[F]],
  )(implicit I: Init[F, G, Ctx, Request_]): HttpRoutes[F] =
    Kleisli[OptionT[F, *], Request[F], Response[F]] { req =>
      val fa =
        for {
          ctx <- I.ask[Ctx]
          span = getter.get(ctx)
          _ <- I.lift(span.putAll(Http4sHeaders.requestFields(req, dropHeadersWhen): _*))
          resp <-
            routes
              .run(req.mapK(I.liftK))
              .map(_.mapK(I.provideK(ctx)))
              .semiflatTap { res =>
                I.lift(span.setStatus(Http4sStatusMapping.toSpanStatus(res.status))) *>
                  I.lift(span.putAll(Http4sHeaders.responseFields(res, dropHeadersWhen): _*))
              }
              .value
        } yield resp

      OptionT[F, Response[F]] {
        I.init(fa)(req)
      }
    }

  def injectApp[F[_], G[_]: Monad, Ctx](
    app: HttpApp[G],
    dropHeadersWhen: CaseInsensitiveString => Boolean,
    getter: Getter[Ctx, Span[F]],
  )(implicit I: Init[F, G, Ctx, Request_]): HttpApp[F] =
    Kleisli[F, Request[F], Response[F]] { req =>
      val fa =
        for {
          ctx <- I.ask[Ctx]
          span = getter.get(ctx)
          _ <- I.lift(span.putAll(Http4sHeaders.requestFields(req, dropHeadersWhen): _*))
          resp <- app.run(req.mapK(I.liftK)).map(_.mapK(I.provideK(ctx)))
          _ <- I.lift(span.setStatus(Http4sStatusMapping.toSpanStatus(resp.status)))
          _ <- I.lift(span.putAll(Http4sHeaders.responseFields(resp, dropHeadersWhen): _*))
        } yield resp

      I.init(fa)(req)
    }
}
