package io.janstenpickle.trace4cats.inject

import cats.data.Kleisli
import cats.effect.IO
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Local
import io.janstenpickle.trace4cats.base.optics.Lens

object LensTest {
  case class Context(userId: String, span: Span[IO])

  implicit val local: Local[Kleisli[IO, Context, *], Span[IO]] =
    Local[Kleisli[IO, Context, *], Context].focus(Lens[Context, Span[IO]](_.span)(s => _.copy(span = s)))

  val trace = Trace[Kleisli[IO, Context, *]]
}
