package io.janstenpickle.trace4cats.http4s.server.zio

import cats.~>
import io.janstenpickle.trace4cats.http4s.common.{Http4sRequestFilter, Http4sSpanNamer}
import io.janstenpickle.trace4cats.http4s.server.ServerTracer
import io.janstenpickle.trace4cats.inject.EntryPoint
import io.janstenpickle.trace4cats.inject.zio.ZIOTrace
import org.http4s.util.CaseInsensitiveString
import org.http4s.{Headers, HttpApp, HttpRoutes}
import zio.Task
import zio.interop.catz._

trait ServerSyntax {
  implicit class TracedRoutes(routes: HttpRoutes[ZIOTrace]) {
    def inject(
      entryPoint: EntryPoint[Task],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    ): HttpRoutes[Task] =
      ServerTracer.injectRoutes[Task, ZIOTrace](
        routes,
        entryPoint,
        λ[Task ~> ZIOTrace](fa => fa),
        span => λ[ZIOTrace ~> Task](_.provide(span)),
        spanNamer,
        requestFilter,
        dropHeadersWhen
      )
  }

  implicit class TracedApp(routes: HttpApp[ZIOTrace]) {
    def inject(
      entryPoint: EntryPoint[Task],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    ): HttpApp[Task] =
      ServerTracer.injectApp[Task, ZIOTrace](
        routes,
        entryPoint,
        λ[Task ~> ZIOTrace](fa => fa),
        span => λ[ZIOTrace ~> Task](_.provide(span)),
        spanNamer,
        requestFilter,
        dropHeadersWhen
      )
  }
}
