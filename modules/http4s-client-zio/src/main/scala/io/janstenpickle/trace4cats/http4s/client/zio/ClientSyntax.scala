package io.janstenpickle.trace4cats.http4s.client.zio

import cats.~>
import io.janstenpickle.trace4cats.ToHeaders
import io.janstenpickle.trace4cats.http4s.client.ClientTracer
import io.janstenpickle.trace4cats.http4s.common.Http4sSpanNamer
import io.janstenpickle.trace4cats.inject.zio.ZIOTrace
import org.http4s.client.Client
import zio.Task
import zio.interop.catz._
import zio.interop.catz.mtl._

trait ClientSyntax {
  implicit class TracedClient(client: Client[Task]) {
    def liftTrace(
      toHeaders: ToHeaders = ToHeaders.w3c,
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath
    ): Client[ZIOTrace] =
      ClientTracer
        .liftTrace[Task, ZIOTrace](
          client,
          λ[Task ~> ZIOTrace](fa => fa),
          span => λ[ZIOTrace ~> Task](_.provide(span)),
          toHeaders,
          spanNamer
        )
  }
}
