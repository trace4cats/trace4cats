package trace4cats

import cats.Monad
import trace4cats.context.Ask

object implicits {

  type SpanContextAsk[F[_]] = Ask[F, SpanContext]

  implicit def SpanContextAsk[F[_]: Monad: Trace.WithContext]: Ask[F, SpanContext] =
    Ask.make(Trace.WithContext[F].context)

  implicit def TraceIdAsk[F[_]: SpanContextAsk]: Ask[F, TraceId] =
    Ask[F, SpanContext].zoom(_.traceId)

  implicit def SpanIdAsk[F[_]: SpanContextAsk]: Ask[F, SpanId] =
    Ask[F, SpanContext].zoom(_.spanId)

}
