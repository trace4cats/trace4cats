package trace4cats

import cats.data.Kleisli
import cats.effect.IO
import trace4cats.context.Local

object KleisliTraceInstanceSummonTest {
  type F[x] = Kleisli[IO, Span[IO], x]
  val t1 = implicitly[Trace[F]]
  val tc1 = implicitly[Trace.WithContext[F]]

  type G[x] = Kleisli[IO, Env[IO], x]
  implicit val gHasLocalSpan: Local[G, Span[IO]] = Local[G, Env[IO]].focus(Env.span)
  val t2 = implicitly[Trace[G]]
  val tc2 = implicitly[Trace.WithContext[G]]
}
