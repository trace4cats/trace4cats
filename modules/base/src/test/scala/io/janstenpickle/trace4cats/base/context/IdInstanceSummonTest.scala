package io.janstenpickle.trace4cats.base.context

object IdInstanceSummonTest {
  type IO[x] = x
  type R = Env
  type F[x] = IO[x]
  type Low[x] = IO[x]

  implicitly[Lift[Low, F]]
  implicitly[Unlift[Low, F]]
}
