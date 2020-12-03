package io.janstenpickle.trace4cats.http4s.server

import cats.Applicative
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.http4s.common.{Http4sHeaders, Http4sRequestFilter, Http4sSpanNamer, Request_}
import io.janstenpickle.trace4cats.inject.{ContextConstructor, SpanParams}
import io.janstenpickle.trace4cats.model.SpanKind

object Http4sContextConstructors {
  def fromHeadersContext[F[_]: Applicative, Ctx](
    makeContext: (Request_, Span[F]) => F[Ctx],
    spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
    requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll
  )(cc: ContextConstructor[F, SpanParams, Span[F]]): ContextConstructor[F, Request_, Ctx] =
    fromHeaders[F](spanNamer, requestFilter)(cc).mapBothF(makeContext)

  def fromHeaders[F[_]: Applicative](
    spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
    requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
  )(cc: ContextConstructor[F, SpanParams, Span[F]]): ContextConstructor[F, Request_, Span[F]] =
    ContextConstructor.instance[F, Request_, Span[F]] { req =>
      val filter = requestFilter.lift(req).getOrElse(true)
      val headers = Http4sHeaders.converter.from(req.headers)

      if (filter) cc.resource((spanNamer(req), SpanKind.Server, headers)) else Span.noop[F]
    }
}
