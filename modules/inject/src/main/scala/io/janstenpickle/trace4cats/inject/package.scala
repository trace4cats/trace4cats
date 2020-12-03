package io.janstenpickle.trace4cats

import io.janstenpickle.trace4cats.model.{SpanKind, TraceHeaders}

package object inject {
  type SpanName = String
  type SpanParams = (SpanName, SpanKind, TraceHeaders)
}
