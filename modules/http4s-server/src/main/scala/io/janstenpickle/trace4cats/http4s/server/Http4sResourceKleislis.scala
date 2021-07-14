package io.janstenpickle.trace4cats.http4s.server

import cats.data.Kleisli
import cats.effect.Resource
import cats.{Applicative, Monad}
import io.janstenpickle.trace4cats.http4s.common.{
  Http4sHeaders,
  Http4sRequestFilter,
  Http4sSpanNamer,
  Http4sStatusMapping,
  Request_
}
import io.janstenpickle.trace4cats.inject.{ResourceKleisli, SpanParams}
import io.janstenpickle.trace4cats.model.SpanKind
import io.janstenpickle.trace4cats.{ErrorHandler, HandledError, Span}
import org.http4s.{Headers, HttpVersion, MessageFailure}
import org.typelevel.ci.CIString

object Http4sResourceKleislis {
  private val messageFailureHandler: ErrorHandler = { case e: MessageFailure =>
    HandledError.Status(Http4sStatusMapping.toSpanStatus(e.toHttpResponse(HttpVersion.`HTTP/1.0`).status))
  }

  def fromHeadersContext[F[_]: Monad, Ctx](
    makeContext: (Request_, Span[F]) => F[Ctx],
    spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
    requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
    dropHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
    errorHandler: ErrorHandler = ErrorHandler.empty,
  )(k: ResourceKleisli[F, SpanParams, Span[F]]): ResourceKleisli[F, Request_, Ctx] =
    fromHeaders[F](spanNamer, requestFilter, dropHeadersWhen, errorHandler)(k).tapWithF { (req, span) =>
      Resource.eval(makeContext(req, span))
    }

  def fromHeaders[F[_]: Applicative](
    spanNamer: Http4sSpanNamer = Http4sSpanNamer.methodWithPath,
    requestFilter: Http4sRequestFilter = Http4sRequestFilter.allowAll,
    dropHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains,
    errorHandler: ErrorHandler = ErrorHandler.empty,
  )(k: ResourceKleisli[F, SpanParams, Span[F]]): ResourceKleisli[F, Request_, Span[F]] =
    Kleisli { req =>
      val filter = requestFilter.lift(req).getOrElse(true)
      lazy val headers = Http4sHeaders.converter.from(req.headers)
      val spanResource =
        if (filter) k.run((spanNamer(req), SpanKind.Server, headers, errorHandler.orElse(messageFailureHandler)))
        else Span.noop[F]

      spanResource.evalTap(_.putAll(Http4sHeaders.requestFields(req, dropHeadersWhen): _*))
    }
}
