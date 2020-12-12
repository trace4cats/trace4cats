package io.janstenpickle.trace4cats.http4s.server

import cats.{Applicative, Monad}
import cats.data.Kleisli
import cats.effect.Resource
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.http4s.common.{Http4sHeaders, Http4sRequestFilter, Http4sSpanNamer, Request_}
import io.janstenpickle.trace4cats.inject.{ResourceKleisli, SpanParams}
import io.janstenpickle.trace4cats.model.SpanKind

object Http4sResourceReaders {
  def fromHeadersContext[F[_]: Monad, Ctx](
    makeContext: (Request_, Span[F]) => F[Ctx],
    spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
    requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll
  )(reader: ResourceKleisli[F, SpanParams, Span[F]]): ResourceKleisli[F, Request_, Ctx] =
    fromHeaders[F](spanNamer, requestFilter)(reader).tapWithF { (req, span) =>
      Resource.liftF(makeContext(req, span))
    }

  def fromHeaders[F[_]: Applicative](
    spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
    requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
  )(reader: ResourceKleisli[F, SpanParams, Span[F]]): ResourceKleisli[F, Request_, Span[F]] =
    Kleisli { req =>
      val filter = requestFilter.lift(req).getOrElse(true)
      val headers = Http4sHeaders.converter.from(req.headers)

      if (filter) reader.run((spanNamer(req), SpanKind.Server, headers)) else Span.noop[F]
    }
}
