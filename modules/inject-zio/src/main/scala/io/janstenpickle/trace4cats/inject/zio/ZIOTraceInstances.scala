package io.janstenpickle.trace4cats.inject.zio

trait ZIOTraceInstances {
  implicit val spannedRIOTrace: SpannedRIOTracer = new SpannedRIOTracer
}
