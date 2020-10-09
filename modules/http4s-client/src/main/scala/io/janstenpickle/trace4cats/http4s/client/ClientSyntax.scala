package io.janstenpickle.trace4cats.http4s.client

import cats.data.Kleisli
import cats.effect.Sync
import io.janstenpickle.trace4cats.http4s.common.Http4sSpanNamer
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import org.http4s.client.Client

trait ClientSyntax {
  implicit class TracedClient[F[_]](client: Client[F]) {
    def liftTrace(toHeaders: ToHeaders = ToHeaders.w3c, spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath)(
      implicit F: Sync[F]
    ): Client[Kleisli[F, Span[F], *]] =
      ClientTracer
        .liftTrace[F, Kleisli[F, Span[F], *]](client, Kleisli.liftK[F, Span[F]], Kleisli.applyK, toHeaders, spanNamer)
  }
}
