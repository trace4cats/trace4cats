package io.janstenpickle.trace4cats.http4s.server

import cats.effect.BracketThrow
import cats.syntax.applicative._
import cats.syntax.flatMap._
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Inject
import io.janstenpickle.trace4cats.http4s.common.{Http4sHeaders, Http4sRequestFilter, Http4sSpanNamer, Request_}
import io.janstenpickle.trace4cats.inject.EntryPoint
import io.janstenpickle.trace4cats.model.SpanKind

object ServerInject {
  def context[F[_]: BracketThrow, Ctx](
    entryPoint: EntryPoint[F],
    spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
    requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
    makeContext: (Request_, Span[F]) => F[Ctx]
  ): Inject[F, Ctx, Request_] = new Inject[F, Ctx, Request_] {
    override def apply[A](req: Request_)(f: Ctx => F[A]): F[A] = {
      val filter = requestFilter.lift(req).getOrElse(true)
      val headers = Http4sHeaders.converter.from(req.headers)

      val spanR = if (filter) entryPoint.continueOrElseRoot(spanNamer(req), SpanKind.Server, headers) else Span.noop[F]

      spanR.use(span => makeContext(req, span).flatMap(f))
    }
  }

  def span[F[_]: BracketThrow](
    entryPoint: EntryPoint[F],
    spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
    requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll
  ): Inject[F, Span[F], Request_] =
    context[F, Span[F]](entryPoint, spanNamer, requestFilter, (_, span) => span.pure[F])
}
