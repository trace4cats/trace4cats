package io.janstenpickle.trace4cats.http4s.client

import cats.Applicative
import cats.effect.Sync
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.base.optics.{Getter, Lens}
import io.janstenpickle.trace4cats.http4s.common.Http4sSpanNamer
import io.janstenpickle.trace4cats.model.TraceHeaders
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import org.http4s.client.Client

trait ClientSyntax {
  implicit class TracedClient[F[_], G[_]](client: Client[F]) {
    def liftTrace(
      toHeaders: ToHeaders = ToHeaders.all,
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath
    )(implicit P: Provide[F, G, Span[F]], F: Applicative[F], G: Sync[G]): Client[G] =
      ClientTracer
        .liftTrace[F, G, Span[F]](client, Lens.id, Getter((toHeaders.fromContext _).compose(_.context)), spanNamer)

    def liftTraceContext[Ctx](
      spanLens: Lens[Ctx, Span[F]],
      headersGetter: Getter[Ctx, TraceHeaders],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath
    )(implicit P: Provide[F, G, Ctx], F: Applicative[F], G: Sync[G]): Client[G] =
      ClientTracer
        .liftTrace[F, G, Ctx](client, spanLens, headersGetter, spanNamer)
  }
}
