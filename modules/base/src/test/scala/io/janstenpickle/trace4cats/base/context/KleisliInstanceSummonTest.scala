package io.janstenpickle.trace4cats.base.context

import cats.data.Kleisli
import cats.catsInstancesForId
import io.janstenpickle.trace4cats.base.context.Env.{Sub1, Sub2}

object KleisliInstanceSummonTest {
  type IO[x] = x
  type R = Env
  type F[x] = Kleisli[IO, R, x]
  type Low[x] = IO[x]

  implicitly[Lift[Low, F]]
  implicitly[Unlift[Low, F]]
  implicitly[Ask[F, R]]
  implicitly[Local[F, R]]
  implicitly[Provide[Low, F, R]]

  implicit val zoomed: Ask[F, Sub1] = Ask[F, R].zoom(Env.sub1)
  implicitly[Ask[F, Sub1]]

  implicit val focused: Local[F, Sub2] = Local[F, R].focus(Env.sub2)
  implicitly[Local[F, Sub2]]
}
