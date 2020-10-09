package io.janstenpickle.trace4cats.inject

import _root_.zio.{Task, ZIO}
import io.janstenpickle.trace4cats.Span

package object zio extends ZIOTraceInstances with ZIOTimer {
  type ZIOTrace[A] = ZIO[Span[Task], Throwable, A]
}
