package io.janstenpickle.trace4cats

import _root_.fs2.Stream
import cats.data.WriterT
import trace4cats.kernel.Span

package object fs2 {
  type TracedStream[F[_], A] = WriterT[Stream[F, *], Span[F], A]
}
