package io.janstenpickle.trace4cats

import cats.data.Kleisli
import cats.effect.Resource
import io.janstenpickle.trace4cats.model.{SpanKind, TraceHeaders}

package object inject {
  type SpanName = String
  type SpanParams = (SpanName, SpanKind, TraceHeaders)
  type ResourceReader[F[_], -In, Out] = Kleisli[Resource[F, *], In, Out]
}
