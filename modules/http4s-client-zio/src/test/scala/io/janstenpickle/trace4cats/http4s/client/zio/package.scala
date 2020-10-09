package io.janstenpickle.trace4cats.http4s.client

import cats.effect.ConcurrentEffect
import _root_.zio.{Runtime, Task}
import _root_.zio.interop.catz._

package object zio {
  implicit val ce: ConcurrentEffect[Task] = Runtime.default.unsafeRun(Task.concurrentEffect)
}
