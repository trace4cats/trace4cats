package io.janstenpickle.trace4cats.sttp.client3

import cats.effect.kernel.{Async, MonadCancelThrow}
import cats.syntax.flatMap._
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.base.optics.{Getter, Lens}
import io.janstenpickle.trace4cats.model.{SampleDecision, SpanKind, TraceHeaders}
import io.janstenpickle.trace4cats.sttp.common.{SttpHeaders, SttpStatusMapping}
import sttp.capabilities.{Effect => SttpEffect}
import sttp.client3.impl.cats.implicits._
import sttp.client3.{HttpError, Request, Response, SttpBackend}
import sttp.monad.{MonadError => SttpMonadError}

class SttpBackendTracer[F[_], G[_], +P, Ctx](
  backend: SttpBackend[F, P],
  spanLens: Lens[Ctx, Span[F]],
  headersGetter: Getter[Ctx, TraceHeaders],
  spanNamer: SttpSpanNamer
)(implicit P: Provide[F, G, Ctx], F: MonadCancelThrow[F], G: Async[G])
    extends SttpBackend[G, P] {
  def send[T, R >: P with SttpEffect[G]](request: Request[T, R]): G[Response[T]] =
    P.kleislift { parentCtx =>
      val parentSpan = spanLens.get(parentCtx)
      parentSpan
        .child(
          spanNamer(request),
          SpanKind.Client,
          { case HttpError(body, statusCode) =>
            SttpStatusMapping.statusToSpanStatus(body.toString, statusCode)
          }
        )
        .use { childSpan =>
          val childCtx = spanLens.set(childSpan)(parentCtx)
          val lower = P.provideK(childCtx)
          val ctxBackend = backend.mapK(P.liftK, lower)

          val headers = headersGetter.get(childCtx)
          val req = request.headers(SttpHeaders.converter.to(headers).headers: _*)

          // only extract request attributes if the span is sampled as the host parsing is quite expensive
          val requestAttributes =
            if (childSpan.context.traceFlags.sampled == SampleDecision.Include)
              childSpan.putAll(SttpRequest.toAttributes(request))
            else F.unit

          requestAttributes >> lower(ctxBackend.send(req))
            .flatTap { resp =>
              childSpan.setStatus(SttpStatusMapping.statusToSpanStatus(resp.statusText, resp.code))
            }
        }
    }

  def close(): G[Unit] = P.lift(backend.close())

  def responseMonad: SttpMonadError[G] = implicitly
}
