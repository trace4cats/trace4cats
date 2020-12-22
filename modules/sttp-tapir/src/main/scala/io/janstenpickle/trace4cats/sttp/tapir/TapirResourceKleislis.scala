package io.janstenpickle.trace4cats.sttp.tapir

import cats.{Applicative, Monad}
import cats.syntax.applicativeError._
import cats.data.{EitherT, Kleisli}
import cats.effect.{MonadThrow, Resource}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.optics.Getter
import io.janstenpickle.trace4cats.inject.{ResourceKleisli, SpanParams}
import io.janstenpickle.trace4cats.model.SpanKind
import io.janstenpickle.trace4cats.sttp.common.SttpHeaders
import sttp.model.{HeaderNames, Headers}

import scala.reflect.ClassTag

object TapirResourceKleislis {
  def fromHeaders[F[_]: Applicative, I](
    headersGetter: Getter[I, Headers],
    inputSpanNamer: TapirInputSpanNamer[I],
    dropHeadersWhen: String => Boolean = HeaderNames.isSensitive,
  )(k: ResourceKleisli[F, SpanParams, Span[F]]): ResourceKleisli[F, I, Span[F]] =
    Kleisli { input =>
      val headers = headersGetter.get(input)
      val traceHeaders = SttpHeaders.converter.from(headers)
      val spanResource = k.run((inputSpanNamer(input), SpanKind.Server, traceHeaders))

      spanResource.evalTap(_.putAll(SttpHeaders.headerFields(headers, dropHeadersWhen): _*))
    }

  def fromHeadersContext[F[_]: Monad, I, E, Ctx](
    makeContext: (I, Span[F]) => F[Either[E, Ctx]],
    headersGetter: Getter[I, Headers],
    inputSpanNamer: TapirInputSpanNamer[I],
    errorToSpanStatus: TapirStatusMapping[E],
    dropHeadersWhen: String => Boolean = HeaderNames.isSensitive
  )(k: ResourceKleisli[F, SpanParams, Span[F]]): ResourceKleisli[F, I, Either[E, Ctx]] =
    fromHeaders(headersGetter, inputSpanNamer, dropHeadersWhen)(k).tapWithF { (req, span) =>
      val fa = EitherT(makeContext(req, span))
        .leftSemiflatTap(e => span.setStatus(errorToSpanStatus(e)))
        .value
      Resource.liftF(fa)
    }

  def fromHeadersContextRecoverErrors[F[_]: MonadThrow, I, E <: Throwable: ClassTag, Ctx](
    makeContext: (I, Span[F]) => F[Ctx],
    headersGetter: Getter[I, Headers],
    inputSpanNamer: TapirInputSpanNamer[I],
    errorToSpanStatus: TapirStatusMapping[E],
    dropHeadersWhen: String => Boolean = HeaderNames.isSensitive
  )(k: ResourceKleisli[F, SpanParams, Span[F]]): ResourceKleisli[F, I, Ctx] =
    fromHeaders(headersGetter, inputSpanNamer, dropHeadersWhen)(k).tapWithF { (req, span) =>
      val fa = makeContext(req, span).onError { case e: E =>
        span.setStatus(errorToSpanStatus(e))
      }
      Resource.liftF(fa)
    }

}
