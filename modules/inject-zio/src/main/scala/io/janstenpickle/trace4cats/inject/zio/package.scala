package io.janstenpickle.trace4cats.inject

import _root_.zio.{RIO, Task}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.zio.ZIOContextInstances

package object zio extends ZIOTraceInstances with ZIOContextInstances {
  type SpannedRIO[A] = RIO[Span[Task], A]
}
