package io.janstenpickle.trace4cats.http4s

import org.http4s.{Request, Response}

package object common {
  type AnyK[_] = Any

  /** Existential versions of `Request` and Response` used to prevent access to their bodies or triggering effects.
    */
  type Request_ = Request[AnyK]
  @inline implicit def Request_[F[_]](req: Request[F]): Request_ = req.covary[AnyK]

  type Response_ = Response[AnyK]
  @inline implicit def Response_[F[_]](resp: Response[F]): Response_ = resp.covary[AnyK]

  type Http4sSpanNamer = Request_ => String

  type Http4sRequestFilter = PartialFunction[Request_, Boolean]
}
