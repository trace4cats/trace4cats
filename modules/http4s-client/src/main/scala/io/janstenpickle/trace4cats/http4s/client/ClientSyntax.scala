package io.janstenpickle.trace4cats.http4s.client

import cats.Applicative
import cats.effect.Sync
import cats.mtl.Ask
import io.janstenpickle.trace4cats.http4s.common.Http4sSpanNamer
import io.janstenpickle.trace4cats.inject.{LiftTrace, Provide}
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import org.http4s.client.Client

trait ClientSyntax {
  implicit class TracedClient[F[_], G[_]](client: Client[F]) {
    def liftTrace(toHeaders: ToHeaders = ToHeaders.all, spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath)(
      implicit F: Applicative[F],
      G: Sync[G],
      ask: Ask[G, Span[F]],
      provide: Provide[F, G],
      lift: LiftTrace[F, G]
    ): Client[G] =
      ClientTracer
        .liftTrace[F, G](client, toHeaders, spanNamer)
  }
}
