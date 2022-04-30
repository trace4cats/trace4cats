package trace4cats

import cats.data.Kleisli
import cats.effect.IO
import trace4cats.context.Local
import trace4cats.kernel.Span

object KleisliTraceInstanceSummonTest {
  type F[x] = Kleisli[IO, Span[IO], x]
  implicitly[Trace[F]]
  implicitly[Trace.WithContext[F]]

  type G[x] = Kleisli[IO, Env[IO], x]
  implicit val gHasLocalSpan: Local[G, Span[IO]] = Local[G, Env[IO]].focus(Env.span)
  implicitly[Trace[G]]
  implicitly[Trace.WithContext[G]]
}
