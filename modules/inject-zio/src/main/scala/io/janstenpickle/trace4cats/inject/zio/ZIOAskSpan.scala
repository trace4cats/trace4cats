package io.janstenpickle.trace4cats.inject.zio

import cats.Applicative
import cats.mtl.Ask
import io.janstenpickle.trace4cats.Span
import zio.{Task, ZIO}
import zio.interop.catz.core._

trait ZIOAskSpan {
  implicit val zioAskSpan: Ask[ZIOTrace, Span[Task]] = new Ask[ZIOTrace, Span[Task]] {
    override def applicative: Applicative[ZIOTrace] = implicitly
    override def ask[E2 >: Span[Task]]: ZIOTrace[E2] = ZIO.environment
  }
}
