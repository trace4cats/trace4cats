package io.janstenpickle.trace4cats.http4s.server

import cats.Monad
import cats.effect.BracketThrow
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.http4s.common.{Http4sRequestFilter, Http4sSpanNamer, Http4sStatusMapping, Request_}
import io.janstenpickle.trace4cats.inject.{EntryPoint, ResourceKleisli, Trace}
import io.janstenpickle.trace4cats.model.SpanStatus
import org.http4s._
import org.http4s.util.CaseInsensitiveString

trait ServerSyntax {
  val messageFailureHandler: PartialFunction[Throwable, SpanStatus] = { case e: MessageFailure =>
    Http4sStatusMapping.toSpanStatus(e.toHttpResponse(HttpVersion.`HTTP/1.0`).status)
  }

  implicit class TracedRoutes[F[_], G[_]](routes: HttpRoutes[G]) {
    def inject(
      entryPoint: EntryPoint[F],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit P: Provide[F, G, Span[F]], F: BracketThrow[F], G: Monad[G], trace: Trace[G]): HttpRoutes[F] = {
      val context =
        Http4sResourceKleislis.fromHeaders(spanNamer, requestFilter, dropHeadersWhen, messageFailureHandler)(
          entryPoint.toKleisli
        )

      ServerTracer.injectRoutes(routes, context, dropHeadersWhen)
    }

    def traced(
      k: ResourceKleisli[F, Request_, Span[F]],
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit P: Provide[F, G, Span[F]], F: BracketThrow[F], G: Monad[G], trace: Trace[G]): HttpRoutes[F] =
      ServerTracer.injectRoutes(routes, k, dropHeadersWhen)

    def injectContext[Ctx](
      entryPoint: EntryPoint[F],
      makeContext: (Request_, Span[F]) => F[Ctx],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit P: Provide[F, G, Ctx], F: BracketThrow[F], G: Monad[G], trace: Trace[G]): HttpRoutes[F] = {
      val context =
        Http4sResourceKleislis.fromHeadersContext(
          makeContext,
          spanNamer,
          requestFilter,
          dropHeadersWhen,
          messageFailureHandler
        )(entryPoint.toKleisli)

      ServerTracer.injectRoutes(routes, context, dropHeadersWhen)
    }

    def tracedContext[Ctx](
      k: ResourceKleisli[F, Request_, Ctx],
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit P: Provide[F, G, Ctx], F: BracketThrow[F], G: Monad[G], trace: Trace[G]): HttpRoutes[F] =
      ServerTracer.injectRoutes(routes, k, dropHeadersWhen)

  }

  implicit class TracedHttpApp[F[_], G[_]](app: HttpApp[G]) {
    def inject(
      entryPoint: EntryPoint[F],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit P: Provide[F, G, Span[F]], F: BracketThrow[F], G: Monad[G], trace: Trace[G]): HttpApp[F] = {
      val context =
        Http4sResourceKleislis.fromHeaders(spanNamer, requestFilter, dropHeadersWhen, messageFailureHandler)(
          entryPoint.toKleisli
        )

      ServerTracer.injectApp(app, context, dropHeadersWhen)
    }

    def traced(
      k: ResourceKleisli[F, Request_, Span[F]],
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit P: Provide[F, G, Span[F]], F: BracketThrow[F], G: Monad[G], trace: Trace[G]): HttpApp[F] =
      ServerTracer.injectApp(app, k, dropHeadersWhen)

    def injectContext[Ctx](
      entryPoint: EntryPoint[F],
      makeContext: (Request_, Span[F]) => F[Ctx],
      spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
      requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit P: Provide[F, G, Ctx], F: BracketThrow[F], G: Monad[G], trace: Trace[G]): HttpApp[F] = {
      val context =
        Http4sResourceKleislis.fromHeadersContext(
          makeContext,
          spanNamer,
          requestFilter,
          dropHeadersWhen,
          messageFailureHandler
        )(entryPoint.toKleisli)

      ServerTracer.injectApp(app, context, dropHeadersWhen)
    }

    def tracedContext[Ctx](
      k: ResourceKleisli[F, Request_, Ctx],
      dropHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains
    )(implicit P: Provide[F, G, Ctx], F: BracketThrow[F], G: Monad[G], trace: Trace[G]): HttpApp[F] =
      ServerTracer.injectApp(app, k, dropHeadersWhen)

  }
}
