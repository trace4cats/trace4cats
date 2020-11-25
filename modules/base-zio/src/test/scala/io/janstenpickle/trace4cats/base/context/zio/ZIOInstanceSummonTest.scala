package io.janstenpickle.trace4cats.base.context.zio

import io.janstenpickle.trace4cats.base.context.{Ask, Lift, Local, Provide, Unlift}
import zio.{IO, ZIO}

object ZIOInstanceSummonTest {
  type R
  type E
  type F[x] = ZIO[R, E, x]
  type Low[x] = IO[E, x]

  implicitly[Lift[Low, F]]
  implicitly[Unlift[Low, F]]
  implicitly[Ask[F, R]]
  implicitly[Local[F, R]]
  implicitly[Provide[Low, F, R]]
}
