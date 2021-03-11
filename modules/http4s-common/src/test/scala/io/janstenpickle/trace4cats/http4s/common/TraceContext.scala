package io.janstenpickle.trace4cats.http4s.common

import java.util.UUID
import cats.effect.kernel.{MonadCancelThrow, Sync}
import cats.syntax.applicative._
import cats.syntax.functor._
import io.janstenpickle.trace4cats.base.optics.{Getter, Lens}
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import io.janstenpickle.trace4cats.model.TraceHeaders
import org.typelevel.ci.CIString

case class TraceContext[F[_]](correlationId: String, span: Span[F])

object TraceContext {
  def make[F[_]: Sync](req: Request_, span: Span[F]): F[TraceContext[F]] =
    req.headers
      .get(CIString("X-Correlation-ID"))
      .fold(Sync[F].delay(UUID.randomUUID().toString))(h => h.head.value.pure)
      .map(TraceContext(_, span))

  def empty[F[_]: MonadCancelThrow]: F[TraceContext[F]] =
    Span.noop[F].use(span => TraceContext[F]("", span).pure[F])

  def span[F[_]]: Lens[TraceContext[F], Span[F]] = Lens[TraceContext[F], Span[F]](_.span)(s => _.copy(span = s))
  def headers[F[_]](toHeaders: ToHeaders): Getter[TraceContext[F], TraceHeaders] =
    ctx => toHeaders.fromContext(ctx.span.context) + ("X-Correlation-ID" -> ctx.correlationId)
}
