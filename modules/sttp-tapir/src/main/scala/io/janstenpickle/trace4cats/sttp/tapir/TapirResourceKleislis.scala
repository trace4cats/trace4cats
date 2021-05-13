package io.janstenpickle.trace4cats.sttp.tapir

import cats.{Monad, MonadThrow}
import cats.syntax.applicativeError._
import cats.data.{EitherT, Kleisli}
import cats.effect.kernel.Resource
import io.janstenpickle.trace4cats.{ErrorHandler, Span}
import io.janstenpickle.trace4cats.base.optics.Getter
import io.janstenpickle.trace4cats.inject.{ResourceKleisli, SpanParams}
import io.janstenpickle.trace4cats.model.SpanKind
import io.janstenpickle.trace4cats.sttp.common.SttpHeaders
import sttp.model.{HeaderNames, Headers}

import scala.reflect.ClassTag

object TapirResourceKleislis {
  def fromHeaders[F[_], I](
    inHeadersGetter: Getter[I, Headers],
    inSpanNamer: TapirInputSpanNamer[I],
    dropHeadersWhen: String => Boolean = HeaderNames.isSensitive
  )(k: ResourceKleisli[F, SpanParams, Span[F]]): ResourceKleisli[F, I, Span[F]] =
    Kleisli { input =>
      val headers = inHeadersGetter.get(input)
      val traceHeaders = SttpHeaders.converter.from(headers)
      val spanResource = k.run((inSpanNamer(input), SpanKind.Server, traceHeaders, ErrorHandler.empty))

      spanResource.evalTap(_.putAll(SttpHeaders.requestFields(headers, dropHeadersWhen): _*))
    }

  def fromHeadersContext[F[_]: Monad, I, E, Ctx](
    makeContext: (I, Span[F]) => F[Either[E, Ctx]],
    inHeadersGetter: Getter[I, Headers],
    inSpanNamer: TapirInputSpanNamer[I],
    errorToSpanStatus: TapirStatusMapping[E],
    dropHeadersWhen: String => Boolean = HeaderNames.isSensitive
  )(k: ResourceKleisli[F, SpanParams, Span[F]]): ResourceKleisli[F, I, Either[E, Ctx]] =
    fromHeaders(inHeadersGetter, inSpanNamer, dropHeadersWhen)(k).tapWithF { (req, span) =>
      val fa = EitherT(makeContext(req, span))
        .leftSemiflatTap(e => span.setStatus(errorToSpanStatus(e)))
        .value
      Resource.eval(fa)
    }

  def fromHeadersContextRecoverErrors[F[_]: MonadThrow, I, E <: Throwable: ClassTag, Ctx](
    makeContext: (I, Span[F]) => F[Ctx],
    inHeadersGetter: Getter[I, Headers],
    inSpanNamer: TapirInputSpanNamer[I],
    errorToSpanStatus: TapirStatusMapping[E],
    dropHeadersWhen: String => Boolean = HeaderNames.isSensitive
  )(k: ResourceKleisli[F, SpanParams, Span[F]]): ResourceKleisli[F, I, Ctx] =
    fromHeaders(inHeadersGetter, inSpanNamer, dropHeadersWhen)(k).tapWithF { (req, span) =>
      val fa = makeContext(req, span).onError { case e: E =>
        span.setStatus(errorToSpanStatus(e))
      }
      Resource.eval(fa)
    }

}
