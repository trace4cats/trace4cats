package trace4cats.context

object IdInstanceSummonTest {
  type F[x] = Option[x]
  type Low[x] = Option[x]

  implicitly[Lift[Low, F]]
  implicitly[Unlift[Low, F]]
}
