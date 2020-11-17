package io.janstenpickle.trace4cats.natchez

import io.janstenpickle.trace4cats.model.TraceHeaders
import natchez.Kernel

object KernelConverter extends TraceHeaders.Converter[Kernel] {
  def from(t: Kernel): TraceHeaders = TraceHeaders(t.toHeaders)
  def to(h: TraceHeaders): Kernel = Kernel(h.values)
}
