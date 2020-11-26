package io.janstenpickle.trace4cats.base.context.zio

import io.janstenpickle.trace4cats.base.context._
import _root_.zio.{Has, ZEnv, ZIO}

object ZIOLayeredInstanceSummonTest {
  case class R()
  type ZR = Has[R]
  type E
  type Low[x] = ZIO[ZEnv, E, x]

  type F[x] = ZIO[ZEnv with ZR, E, x]
  implicitly[Lift[Low, F]]
  implicitly[Unlift[Low, F]]
  implicitly[Ask[F, R]]
  implicitly[Local[F, R]]
  implicitly[Provide[Low, F, R]]

  type G[x] = ZIO[ZR with ZEnv, E, x]
  implicitly[Lift[Low, G]]
  implicitly[Unlift[Low, G]]
  implicitly[Ask[G, R]]
  implicitly[Local[G, R]]
  implicitly[Provide[Low, G, R]]
}
