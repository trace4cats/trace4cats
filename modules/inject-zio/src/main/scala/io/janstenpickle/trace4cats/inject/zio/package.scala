package io.janstenpickle.trace4cats.inject

import _root_.zio.{Task, ZIO}
import io.janstenpickle.trace4cats.Span
import _root_.zio.Has
import _root_.zio.RIO

package object zio extends ZIOTraceInstances with ZIOTimer with ZIOAskSpan with ZIOUnliftProvide {
  type ZIOTrace[A] = ZIO[Span[Task], Throwable, A]

  type ZSpan = Has[Span[Task]]
  type ZIOTraceLayered[R, A] = RIO[R with ZSpan, A]
}
