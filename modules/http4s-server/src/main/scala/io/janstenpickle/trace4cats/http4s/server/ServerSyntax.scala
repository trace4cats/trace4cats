package io.janstenpickle.trace4cats.http4s.server

import cats.Monad
import cats.effect.Bracket
import io.janstenpickle.trace4cats.http4s.common.{Http4sRequestFilter, Http4sSpanNamer}
import io.janstenpickle.trace4cats.inject.{EntryPoint, LiftTrace, Provide}
import org.http4s._
import org.http4s.util.CaseInsensitiveString

trait ServerSyntax {
  implicit class TracedRoutes[F[_], G[_]](routes: HttpRoutes[G]) {
    def inject(
      entryPoint: EntryPoint[F],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit F: Bracket[F, Throwable], provide: Provide[F, G], lift: LiftTrace[F, G]): HttpRoutes[F] =
      ServerTracer
        .injectRoutes[F, G](routes, entryPoint, spanNamer, requestFilter, dropHeadersWhen)
  }

  implicit class TracedHttpApp[F[_], G[_]: Monad](app: HttpApp[G]) {
    def inject(
      entryPoint: EntryPoint[F],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit F: Bracket[F, Throwable], provide: Provide[F, G], lift: LiftTrace[F, G]): HttpApp[F] =
      ServerTracer.injectApp[F, G](app, entryPoint, spanNamer, requestFilter, dropHeadersWhen)

  }
}
