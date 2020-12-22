package io.janstenpickle.trace4cats.sttp

import io.janstenpickle.trace4cats.inject.SpanName
import io.janstenpickle.trace4cats.model.SpanStatus
import sttp.tapir.Endpoint

package object tapir {
  type TapirSpanNamer[I] = (Endpoint[I, _, _, _], I) => SpanName
  type TapirInputSpanNamer[I] = I => SpanName
  type TapirStatusMapping[E] = E => SpanStatus
}
