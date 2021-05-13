package io.janstenpickle.trace4cats.sttp.tapir

import cats.Monad
import cats.data.EitherT
import cats.effect.kernel.MonadCancelThrow
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.base.optics.Getter
import io.janstenpickle.trace4cats.inject.{ResourceKleisli, Trace}
import io.janstenpickle.trace4cats.sttp.common.SttpHeaders
import sttp.model.Headers
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.integ.cats.MonadErrorSyntax._

import scala.reflect.ClassTag

object ServerEndpointTracer {
  def inject[I, E, O, R, F[_], G[_], Ctx](
    serverEndpoint: ServerEndpoint[I, E, O, R, G],
    k: ResourceKleisli[F, I, Either[E, Ctx]],
    inHeadersGetter: Getter[I, Headers],
    outHeadersGetter: Getter[O, Headers],
    errorToSpanStatus: TapirStatusMapping[E],
    dropHeadersWhen: String => Boolean
  )(implicit P: Provide[F, G, Ctx], F: MonadCancelThrow[F], G: Monad[G], T: Trace[G]): ServerEndpoint[I, E, O, R, F] =
    serverEndpoint.copy(logic =
      MEF =>
        input => {
          k(input).use {
            EitherT
              .fromEither[F](_)
              .flatMap { ctx =>
                val lower = P.provideK(ctx)
                val MEG = MEF.imapK(P.liftK)(lower)
                EitherT {
                  Trace[G].putAll(SttpHeaders.requestFields(inHeadersGetter.get(input), dropHeadersWhen): _*) >>
                    serverEndpoint.logic(MEG)(input)
                }.semiflatTap { output =>
                  Trace[G].putAll(SttpHeaders.responseFields(outHeadersGetter.get(output), dropHeadersWhen): _*)
                }.leftSemiflatTap { err =>
                  Trace[G].setStatus(errorToSpanStatus(err))
                }.mapK(lower)
              }
              .value
          }
        }
    )

  def injectRecoverErrors[I, E <: Throwable, O, R, F[_], G[_], Ctx](
    serverEndpoint: ServerEndpoint[I, E, O, R, G],
    k: ResourceKleisli[F, I, Ctx],
    inHeadersGetter: Getter[I, Headers],
    outHeadersGetter: Getter[O, Headers],
    errorToSpanStatus: TapirStatusMapping[E],
    dropHeadersWhen: String => Boolean
  )(implicit
    P: Provide[F, G, Ctx],
    F: MonadCancelThrow[F],
    G: Monad[G],
    T: Trace[G],
    eClassTag: ClassTag[E]
  ): ServerEndpoint[I, E, O, R, F] =
    serverEndpoint.copy(logic =
      inject(
        serverEndpoint,
        k.attemptNarrow[E],
        inHeadersGetter,
        outHeadersGetter,
        errorToSpanStatus,
        dropHeadersWhen
      ).logic
    )
}
