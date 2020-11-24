package io.janstenpickle.trace4cats.http4s

import java.util.concurrent.Executors

import cats.data.Kleisli
import cats.effect.{ContextShift, IO}
import io.janstenpickle.trace4cats.http4s.common.TraceContext
import io.janstenpickle.trace4cats.inject.Trace
import io.janstenpickle.trace4cats.inject.Trace.KleisliTrace

import scala.concurrent.ExecutionContext

package object client {
  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.fromExecutor(Executors.newCachedThreadPool()))

  implicit val trace: Trace[Kleisli[IO, TraceContext[IO], *]] =
    new KleisliTrace[IO].lens(TraceContext.span[IO].get, (c, s) => TraceContext.span[IO].set(s)(c))
}
