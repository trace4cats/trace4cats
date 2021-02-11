package io.janstenpickle.trace4cats

import cats.data.Kleisli
import cats.effect.Resource
import io.janstenpickle.trace4cats.model.{SpanKind, SpanStatus, TraceHeaders}

import scala.annotation.unchecked.uncheckedVariance

package object inject {
  type SpanName = String
  type SpanParams = (SpanName, SpanKind, TraceHeaders, PartialFunction[Throwable, SpanStatus])
  type ResourceKleisli[F[_], -In, +Out] = Kleisli[Resource[F, +*], In, Out @uncheckedVariance]
}
