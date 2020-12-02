package io.janstenpickle.trace4cats.http4s.server

import cats.Monad
import cats.effect.BracketThrow
import cats.syntax.applicative._
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.{Inject, Provide}
import io.janstenpickle.trace4cats.base.optics.Getter
import io.janstenpickle.trace4cats.http4s.common.{Http4sRequestFilter, Http4sSpanNamer, Request_}
import io.janstenpickle.trace4cats.inject.EntryPoint
import org.http4s._
import org.http4s.util.CaseInsensitiveString

trait ServerSyntax {
  implicit class TracedRoutes[F[_], G[_]: Monad](routes: HttpRoutes[G]) {
    def inject(
      entryPoint: EntryPoint[F],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit P: Provide[F, G, Span[F]], F: BracketThrow[F]): HttpRoutes[F] = {
      implicit val inject: Inject[F, Span[F], Request_] = ServerInject.span(entryPoint, spanNamer, requestFilter)

      ServerTracer.injectRoutes2[F, G, Span[F]](routes, dropHeadersWhen, Getter.id)
    }

    def injectContext[Ctx](
      entryPoint: EntryPoint[F],
      makeContext: (Request_, Span[F]) => F[Ctx],
      getter: Ctx => Span[F],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit P: Provide[F, G, Ctx], F: BracketThrow[F]): HttpRoutes[F] = {
      implicit val inject: Inject[F, Ctx, Request_] =
        ServerInject.ctx(entryPoint, spanNamer, requestFilter, makeContext)

      ServerTracer
        .injectRoutes2(routes, dropHeadersWhen, Getter(getter))
    }

  }

//  implicit class TracedRoutes2[F[_], G[_]: Monad](routes: HttpRoutes[G]) {
//    def inject(
//      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
//    )(implicit I: Init[F, G, Span[F], Request_], F: BracketThrow[F]): HttpRoutes[F] =
//      ServerTracer.injectRoutes2[F, G, Span[F]](routes, dropHeadersWhen, Getter.id)
//
//    def injectContext[Ctx](
//      getter: Ctx => Span[F],
//      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
//    )(implicit I: Init[F, G, Ctx, Request_], F: BracketThrow[F]): HttpRoutes[F] =
//      ServerTracer.injectRoutes2[F, G, Ctx](routes, dropHeadersWhen, Getter(getter))
//  }

  implicit class TracedHttpApp[F[_], G[_]: Monad](app: HttpApp[G]) {
    def inject(
      entryPoint: EntryPoint[F],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit P: Provide[F, G, Span[F]], F: BracketThrow[F]): HttpApp[F] =
      ServerTracer
        .injectApp(app, entryPoint, spanNamer, requestFilter, dropHeadersWhen, (_, s) => s.pure[F])

    def injectContext[Ctx](
      entryPoint: EntryPoint[F],
      makeContext: (Request_, Span[F]) => F[Ctx],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit P: Provide[F, G, Ctx], F: BracketThrow[F]): HttpApp[F] =
      ServerTracer
        .injectApp(app, entryPoint, spanNamer, requestFilter, dropHeadersWhen, makeContext)
  }
}
