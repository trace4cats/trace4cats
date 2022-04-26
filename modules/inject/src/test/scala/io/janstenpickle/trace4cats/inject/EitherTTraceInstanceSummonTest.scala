package io.janstenpickle.trace4cats.inject

import cats.data.{EitherT, Kleisli}
import cats.effect.IO
import io.janstenpickle.trace4cats.Span

object EitherTTraceInstanceSummonTest {
  type F[x] = Kleisli[IO, Span[IO], x]
  implicitly[Trace[EitherT[F, Unit, *]]]
  implicitly[Trace.WithContext[EitherT[F, Unit, *]]]
}
