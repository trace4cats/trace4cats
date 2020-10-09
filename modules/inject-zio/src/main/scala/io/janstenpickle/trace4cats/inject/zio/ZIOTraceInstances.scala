package io.janstenpickle.trace4cats.inject.zio

trait ZIOTraceInstances {
  implicit val zioTrace: ZIOTracer = new ZIOTracer
}
