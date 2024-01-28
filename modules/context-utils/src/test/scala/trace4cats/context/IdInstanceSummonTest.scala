package trace4cats.context

object IdInstanceSummonTest {
  type F[x] = Option[x]
  type Low[x] = Option[x]

  val lift = implicitly[Lift[Low, F]]
  val unlift = implicitly[Unlift[Low, F]]
}
