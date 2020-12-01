package io.janstenpickle.trace4cats.inject

import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.optics.Lens

case class Env[F[_]](dymmy: String, span: Span[F])
object Env {
  def span[F[_]]: Lens[Env[F], Span[F]] = Lens[Env[F], Span[F]](_.span)(s => _.copy(span = s))
}
