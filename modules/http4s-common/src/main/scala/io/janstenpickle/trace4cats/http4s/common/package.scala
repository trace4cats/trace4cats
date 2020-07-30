package io.janstenpickle.trace4cats.http4s

import org.http4s.Request

package object common {

  /**
    * `Http4sSpanNamer` is intentionally existential, since no knowledge about `F[_]`
    * may be used to get a `String` from a `Request[F]`.
    *
    * Note on cross-compiling with Scala 3: `forSome` will no longer be supported,
    * but `_` will become poly-kinded and should be used instead.
    *
    * Proper definition for Scala 3:
    * {{{
    *   type Http4sSpanNamer = Request[_] => String
    * }}}
    */
  type Http4sSpanNamer = (Request[f] forSome { type f[_] }) => String
}
