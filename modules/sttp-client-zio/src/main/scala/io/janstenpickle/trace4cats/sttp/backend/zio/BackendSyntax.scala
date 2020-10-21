package io.janstenpickle.trace4cats.sttp.backend.zio

import io.janstenpickle.trace4cats.ToHeaders
import io.janstenpickle.trace4cats.inject.zio.ZIOTrace
import io.janstenpickle.trace4cats.sttp.backend.SttpSpanNamer
import sttp.client.SttpBackend
import zio.Task

trait BackendSyntax {
  implicit class TracedBackendSyntax[-S, -WS_HANDLER[_]](backend: SttpBackend[Task, S, WS_HANDLER]) {
    def liftTrace(
      toHeaders: ToHeaders = ToHeaders.w3c,
      spanNamer: SttpSpanNamer = SttpSpanNamer.methodWithPath
    ): SttpBackend[ZIOTrace, S, WS_HANDLER] =
      TracedBackend(backend, toHeaders, spanNamer)
  }
}
