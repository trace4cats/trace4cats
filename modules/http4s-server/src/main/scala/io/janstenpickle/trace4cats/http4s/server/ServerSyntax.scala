package io.janstenpickle.trace4cats.http4s.server

import cats.data.Kleisli
import cats.effect.Bracket
import cats.~>
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.http4s.common.{Http4sRequestFilter, Http4sSpanNamer}
import io.janstenpickle.trace4cats.inject.EntryPoint
import org.http4s._
import org.http4s.util.CaseInsensitiveString

trait ServerSyntax {
  implicit class TracedRoutes[F[_]](routes: HttpRoutes[Kleisli[F, Span[F], *]]) {
    def inject(
      entryPoint: EntryPoint[F],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit F: Bracket[F, Throwable]): HttpRoutes[F] = {
      type G[A] = Kleisli[F, Span[F], A]

      ServerTracer.injectRoutes[F, Kleisli[F, Span[F], *]](
        routes,
        entryPoint,
        λ[F ~> G](fa => Kleisli(_ => fa)),
        span => λ[G ~> F](_(span)),
        spanNamer,
        requestFilter,
        dropHeadersWhen
      )
    }
  }

  implicit class TracedHttpApp[F[_], G[_]](app: HttpApp[Kleisli[F, Span[F], *]]) {
    def inject(
      entryPoint: EntryPoint[F],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit F: Bracket[F, Throwable]): HttpApp[F] = {
      type G[A] = Kleisli[F, Span[F], A]

      ServerTracer.injectApp[F, Kleisli[F, Span[F], *]](
        app,
        entryPoint,
        λ[F ~> G](fa => Kleisli(_ => fa)),
        span => λ[G ~> F](_(span)),
        spanNamer,
        requestFilter,
        dropHeadersWhen
      )
    }

  }
}
