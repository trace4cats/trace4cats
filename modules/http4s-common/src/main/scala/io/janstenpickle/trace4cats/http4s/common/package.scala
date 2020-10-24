package io.janstenpickle.trace4cats.http4s

import org.http4s.Request

package object common {
  type AnyK[_] = Any

  /**
    * `Http4sSpanNamer` is intentionally existential via `AnyK`, since no knowledge about `F[_]`
    * may be used to get a `String` from a `Request[F]`.
    */
  type Http4sSpanNamer = Request[AnyK] => String

  type Http4sRequestFilter = PartialFunction[Request[AnyK], Boolean]
}
