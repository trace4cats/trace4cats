package trace4cats.context

import cats.data.Kleisli
import trace4cats.context.Env._

object KleisliInstanceSummonTest {
  type IO[x] = x
  type R = Env
  type F[x] = Kleisli[IO, R, x]
  type Low[x] = IO[x]

  val lift = implicitly[Lift[Low, F]]
  val unlift = implicitly[Unlift[Low, F]]
  val ask = implicitly[Ask[F, R]]
  val local = implicitly[Local[F, R]]
  val provide = implicitly[Provide[Low, F, R]]

  implicit val zoomed: Ask[F, Sub1] = Ask[F, R].zoom(Env.sub1)
  val zoomedAsk = implicitly[Ask[F, Sub1]]

  implicit val focused: Local[F, Sub2] = Local[F, R].focus(Env.sub2)
  val focusedAsk = implicitly[Local[F, Sub2]]
}
