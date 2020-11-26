package io.janstenpickle.trace4cats.inject.zio

import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Local
import io.janstenpickle.trace4cats.base.context.zio._
import io.janstenpickle.trace4cats.inject.Trace
import zio.interop.catz._
import zio.{Has, RIO, Task, ZEnv}

object ZIOTraceInstanceSummonTest {
  type F[x] = RIO[Span[Task], x]
  implicitly[Trace[F]]

  type ZSpan = Has[Span[Task]]
  type G[x] = RIO[ZEnv with ZSpan, x]
  implicit val rioLayeredLocalSpan: Local[G, Span[Task]] = zioProvideSome
  implicitly[Trace[G]]

  type H[x] = RIO[Env, x]
  implicit val rioLocalSpan: Local[H, Span[Task]] = Local[H, Env].focus(Env.span)
  implicitly[Trace[H]]
}
