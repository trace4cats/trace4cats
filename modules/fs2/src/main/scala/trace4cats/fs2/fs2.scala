package trace4cats

import _root_.fs2.Stream
import cats.data.WriterT

package object fs2 {
  type TracedStream[F[_], A] = WriterT[Stream[F, *], Span[F], A]
}
