package io.janstenpickle.trace4cats.http4s.server

import cats.Monad
import cats.effect.BracketThrow
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.{Init, Inject, Provide}
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

      ServerTracer.injectRoutes[F, G, Span[F]](routes, dropHeadersWhen, Getter.id)
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
        ServerInject.context(entryPoint, spanNamer, requestFilter, makeContext)

      ServerTracer.injectRoutes(routes, dropHeadersWhen, getter)
    }

    def traced(dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains)(implicit
      I: Init[F, G, Span[F], Request_]
    ): HttpRoutes[F] = ServerTracer.injectRoutes[F, G, Span[F]](routes, dropHeadersWhen, Getter.id)

    def tracedContext[Ctx](
      getter: Ctx => Span[F],
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit I: Init[F, G, Ctx, Request_]): HttpRoutes[F] =
      ServerTracer.injectRoutes[F, G, Ctx](routes, dropHeadersWhen, getter)
  }

  implicit class TracedHttpApp[F[_], G[_]: Monad](app: HttpApp[G]) {
    def inject(
      entryPoint: EntryPoint[F],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit P: Provide[F, G, Span[F]], F: BracketThrow[F]): HttpApp[F] = {
      implicit val inject: Inject[F, Span[F], Request_] = ServerInject.span(entryPoint, spanNamer, requestFilter)

      ServerTracer.injectApp[F, G, Span[F]](app, dropHeadersWhen, Getter.id)
    }

    def injectContext[Ctx](
      entryPoint: EntryPoint[F],
      makeContext: (Request_, Span[F]) => F[Ctx],
      getter: Ctx => Span[F],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit P: Provide[F, G, Ctx], F: BracketThrow[F]): HttpApp[F] = {
      implicit val inject: Inject[F, Ctx, Request_] =
        ServerInject.context(entryPoint, spanNamer, requestFilter, makeContext)

      ServerTracer.injectApp(app, dropHeadersWhen, Getter(getter))
    }

    def traced(dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains)(implicit
      I: Init[F, G, Span[F], Request_]
    ): HttpApp[F] = ServerTracer.injectApp[F, G, Span[F]](app, dropHeadersWhen, Getter.id)

    def tracedContext[Ctx](
      getter: Ctx => Span[F],
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit I: Init[F, G, Ctx, Request_]): HttpApp[F] =
      ServerTracer.injectApp[F, G, Ctx](app, dropHeadersWhen, getter)
  }
}
