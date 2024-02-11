package trace4cats

import cats.data.{EitherT, Kleisli}
import cats.effect.IO

object EitherTTraceInstanceSummonTest {
  type F[x] = Kleisli[IO, Span[IO], x]
  val t = implicitly[Trace[EitherT[F, Unit, *]]]
  val tc = implicitly[Trace.WithContext[EitherT[F, Unit, *]]]
}
