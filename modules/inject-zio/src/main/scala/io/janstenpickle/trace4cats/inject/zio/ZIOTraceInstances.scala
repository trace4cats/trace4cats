package io.janstenpickle.trace4cats.inject.zio

import cats.~>
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.inject.{LiftTrace, Provide}
import zio.Task
import zio.interop.catz._

trait ZIOTraceInstances {
  implicit val zioTrace: ZIOTracer = new ZIOTracer

  implicit val zioProvide: Provide[Task, ZIOTrace] = new Provide[Task, ZIOTrace] {
    override def fk(span: Span[Task]): ZIOTrace ~> Task = new (ZIOTrace ~> Task) {
      override def apply[A](fa: ZIOTrace[A]): Task[A] = fa.provide(span)
    }

    override val noopFk: ZIOTrace ~> Task = new (ZIOTrace ~> Task) {
      override def apply[A](fa: ZIOTrace[A]): Task[A] = Span.noop[Task].use(fa.provide)
    }
  }

  implicit val zioLift: LiftTrace[Task, ZIOTrace] = new LiftTrace[Task, ZIOTrace] {
    override val fk: Task ~> ZIOTrace = new (Task ~> ZIOTrace) {
      override def apply[A](fa: Task[A]): ZIOTrace[A] = fa
    }
  }
}
