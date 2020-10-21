package io.janstenpickle.trace4cats.sttp.backend.zio

import cats.~>
import io.janstenpickle.trace4cats.ToHeaders
import sttp.client.SttpBackend
import io.janstenpickle.trace4cats.inject.zio.ZIOTrace
import io.janstenpickle.trace4cats.sttp.backend.{BackendTracer, SttpSpanNamer}
import zio.Task
import zio.interop.catz._
import zio.interop.catz.mtl._

case class TracedBackend[-S, -WS_HANDLER[_]](
  backend: SttpBackend[Task, S, WS_HANDLER],
  toHeaders: ToHeaders = ToHeaders.w3c,
  spanNamer: SttpSpanNamer = SttpSpanNamer.methodWithPath
) extends BackendTracer[Task, ZIOTrace, S, WS_HANDLER](backend, λ[Task ~> ZIOTrace](fa => fa), toHeaders, spanNamer)
