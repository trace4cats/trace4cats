package io.janstenpickle.trace4cats.sttp.tapir

import cats.Monad
import cats.effect.BracketThrow
import cats.syntax.either._
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.base.optics.Getter
import io.janstenpickle.trace4cats.inject.{EntryPoint, ResourceKleisli, Trace}
import sttp.model.{HeaderNames, Headers}
import sttp.tapir.server.ServerEndpoint

import scala.reflect.ClassTag

trait ServerEndpointSyntax {
  implicit class TracedServerEndpoint[I, E, O, R, F[_], G[_]](serverEndpoint: ServerEndpoint[I, E, O, R, G]) {
    def inject(
      entryPoint: EntryPoint[F],
      headersGetter: Getter[I, Headers],
      spanNamer: TapirSpanNamer[I] = TapirSpanNamer.methodWithPathTemplate,
      dropHeadersWhen: String => Boolean = HeaderNames.isSensitive,
      errorToSpanStatus: TapirStatusMapping[E] = TapirStatusMapping.errorStringToInternal
    )(implicit
      P: Provide[F, G, Span[F]],
      F: BracketThrow[F],
      G: Monad[G],
      T: Trace[G]
    ): ServerEndpoint[I, E, O, R, F] = {
      val inputSpanNamer = spanNamer(serverEndpoint.endpoint, _)
      val context = TapirResourceKleislis
        .fromHeaders(headersGetter, inputSpanNamer)(entryPoint.toKleisli)
        .map(_.asRight[E])
      ServerEndpointTracer.inject(serverEndpoint, context, headersGetter, errorToSpanStatus, dropHeadersWhen)
    }

    def traced(
      k: ResourceKleisli[F, I, Span[F]],
      headersGetter: Getter[I, Headers],
      dropHeadersWhen: String => Boolean = HeaderNames.isSensitive,
      errorToSpanStatus: TapirStatusMapping[E] = TapirStatusMapping.errorStringToInternal
    )(implicit P: Provide[F, G, Span[F]], F: BracketThrow[F], G: Monad[G], T: Trace[G]): ServerEndpoint[I, E, O, R, F] =
      ServerEndpointTracer.inject(
        serverEndpoint,
        k.map(_.asRight[E]),
        headersGetter,
        errorToSpanStatus,
        dropHeadersWhen
      )

    def injectContext[Ctx](
      entryPoint: EntryPoint[F],
      makeContext: (I, Span[F]) => F[Either[E, Ctx]],
      headersGetter: Getter[I, Headers],
      spanNamer: TapirSpanNamer[I] = TapirSpanNamer.methodWithPathTemplate,
      dropHeadersWhen: String => Boolean = HeaderNames.isSensitive,
      errorToSpanStatus: TapirStatusMapping[E] = TapirStatusMapping.errorStringToInternal
    )(implicit P: Provide[F, G, Ctx], F: BracketThrow[F], G: Monad[G], T: Trace[G]): ServerEndpoint[I, E, O, R, F] = {
      val inputSpanNamer = spanNamer(serverEndpoint.endpoint, _)
      val context = TapirResourceKleislis.fromHeadersContext(
        makeContext,
        headersGetter,
        inputSpanNamer,
        errorToSpanStatus,
        dropHeadersWhen
      )(entryPoint.toKleisli)
      ServerEndpointTracer.inject(serverEndpoint, context, headersGetter, errorToSpanStatus, dropHeadersWhen)
    }

    def tracedContext[Ctx](
      k: ResourceKleisli[F, I, Either[E, Ctx]],
      headersGetter: Getter[I, Headers],
      dropHeadersWhen: String => Boolean = HeaderNames.isSensitive,
      errorToSpanStatus: TapirStatusMapping[E] = TapirStatusMapping.errorStringToInternal
    )(implicit P: Provide[F, G, Ctx], F: BracketThrow[F], G: Monad[G], T: Trace[G]): ServerEndpoint[I, E, O, R, F] =
      ServerEndpointTracer.inject(serverEndpoint, k, headersGetter, errorToSpanStatus, dropHeadersWhen)
  }

  implicit class TracedServerEndpointRecoverErrors[I, E <: Throwable, O, R, F[_], G[_]](
    serverEndpoint: ServerEndpoint[I, E, O, R, G]
  ) {
    def injectContextRecoverErrors[Ctx](
      entryPoint: EntryPoint[F],
      makeContext: (I, Span[F]) => F[Ctx],
      headersGetter: Getter[I, Headers],
      spanNamer: TapirSpanNamer[I] = TapirSpanNamer.methodWithPathTemplate,
      dropHeadersWhen: String => Boolean = HeaderNames.isSensitive,
      errorToSpanStatus: TapirStatusMapping[E] = TapirStatusMapping.errorMessageToInternal
    )(implicit
      P: Provide[F, G, Ctx],
      F: BracketThrow[F],
      G: Monad[G],
      T: Trace[G],
      eClassTag: ClassTag[E]
    ): ServerEndpoint[I, E, O, R, F] = {
      val inputSpanNamer = spanNamer(serverEndpoint.endpoint, _)
      val context = TapirResourceKleislis.fromHeadersContextRecoverErrors(
        makeContext,
        headersGetter,
        inputSpanNamer,
        errorToSpanStatus,
        dropHeadersWhen
      )(entryPoint.toKleisli)

      ServerEndpointTracer.injectRecoverErrors(
        serverEndpoint,
        context,
        headersGetter,
        errorToSpanStatus,
        dropHeadersWhen
      )
    }

    def tracedContextRecoverErrors[Ctx](
      k: ResourceKleisli[F, I, Ctx],
      headersGetter: Getter[I, Headers],
      dropHeadersWhen: String => Boolean = HeaderNames.isSensitive,
      errorToSpanStatus: TapirStatusMapping[E] = TapirStatusMapping.errorStringToInternal
    )(implicit
      P: Provide[F, G, Ctx],
      F: BracketThrow[F],
      G: Monad[G],
      T: Trace[G],
      eClassTag: ClassTag[E]
    ): ServerEndpoint[I, E, O, R, F] =
      ServerEndpointTracer.injectRecoverErrors(serverEndpoint, k, headersGetter, errorToSpanStatus, dropHeadersWhen)
  }
}
