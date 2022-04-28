package io.janstenpickle.trace4cats

import cats.data.Kleisli
import cats.effect.kernel.Resource
import io.janstenpickle.trace4cats.model.{SpanKind, TraceHeaders}

import scala.annotation.unchecked.uncheckedVariance

package object inject {
  type SpanName = String
  type SpanParams = (SpanName, SpanKind, TraceHeaders, ErrorHandler)
  type ResourceKleisli[F[_], -In, +Out] = Kleisli[Resource[F, +*], In, Out @uncheckedVariance]
}
