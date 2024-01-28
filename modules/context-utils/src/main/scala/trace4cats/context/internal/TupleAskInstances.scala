package trace4cats.context.internal

import cats.Monad
import cats.syntax.all._

import trace4cats.context.Ask

// scalafmt: { maxColumn = 500 }
trait TupleAskInstances {

  implicit def Tuple2Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *]]: Ask[Z, (A, B)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask).tupled)

  implicit def Tuple3Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *]]: Ask[Z, (A, B, C)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask).tupled)

  implicit def Tuple4Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *]]: Ask[Z, (A, B, C, D)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask).tupled)

  implicit def Tuple5Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *]]: Ask[Z, (A, B, C, D, E)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask).tupled)

  implicit def Tuple6Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask).tupled)

  implicit def Tuple7Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask).tupled)

  implicit def Tuple8Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *], H: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G, H)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask, Ask[Z, H].ask).tupled)

  implicit def Tuple9Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *], H: Ask[Z, *], I: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G, H, I)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask, Ask[Z, H].ask, Ask[Z, I].ask).tupled)

  implicit def Tuple10Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *], H: Ask[Z, *], I: Ask[Z, *], J: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G, H, I, J)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask, Ask[Z, H].ask, Ask[Z, I].ask, Ask[Z, J].ask).tupled)

  implicit def Tuple11Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *], H: Ask[Z, *], I: Ask[Z, *], J: Ask[Z, *], K: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G, H, I, J, K)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask, Ask[Z, H].ask, Ask[Z, I].ask, Ask[Z, J].ask, Ask[Z, K].ask).tupled)

  implicit def Tuple12Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *], H: Ask[Z, *], I: Ask[Z, *], J: Ask[Z, *], K: Ask[Z, *], L: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G, H, I, J, K, L)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask, Ask[Z, H].ask, Ask[Z, I].ask, Ask[Z, J].ask, Ask[Z, K].ask, Ask[Z, L].ask).tupled)

  implicit def Tuple13Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *], H: Ask[Z, *], I: Ask[Z, *], J: Ask[Z, *], K: Ask[Z, *], L: Ask[Z, *], M: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask, Ask[Z, H].ask, Ask[Z, I].ask, Ask[Z, J].ask, Ask[Z, K].ask, Ask[Z, L].ask, Ask[Z, M].ask).tupled)

  implicit def Tuple14Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *], H: Ask[Z, *], I: Ask[Z, *], J: Ask[Z, *], K: Ask[Z, *], L: Ask[Z, *], M: Ask[Z, *], N: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask, Ask[Z, H].ask, Ask[Z, I].ask, Ask[Z, J].ask, Ask[Z, K].ask, Ask[Z, L].ask, Ask[Z, M].ask, Ask[Z, N].ask).tupled)

  implicit def Tuple15Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *], H: Ask[Z, *], I: Ask[Z, *], J: Ask[Z, *], K: Ask[Z, *], L: Ask[Z, *], M: Ask[Z, *], N: Ask[Z, *], O: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask, Ask[Z, H].ask, Ask[Z, I].ask, Ask[Z, J].ask, Ask[Z, K].ask, Ask[Z, L].ask, Ask[Z, M].ask, Ask[Z, N].ask, Ask[Z, O].ask).tupled)

  implicit def Tuple16Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *], H: Ask[Z, *], I: Ask[Z, *], J: Ask[Z, *], K: Ask[Z, *], L: Ask[Z, *], M: Ask[Z, *], N: Ask[Z, *], O: Ask[Z, *], P: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask, Ask[Z, H].ask, Ask[Z, I].ask, Ask[Z, J].ask, Ask[Z, K].ask, Ask[Z, L].ask, Ask[Z, M].ask, Ask[Z, N].ask, Ask[Z, O].ask, Ask[Z, P].ask).tupled)

  implicit def Tuple17Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *], H: Ask[Z, *], I: Ask[Z, *], J: Ask[Z, *], K: Ask[Z, *], L: Ask[Z, *], M: Ask[Z, *], N: Ask[Z, *], O: Ask[Z, *], P: Ask[Z, *], Q: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask, Ask[Z, H].ask, Ask[Z, I].ask, Ask[Z, J].ask, Ask[Z, K].ask, Ask[Z, L].ask, Ask[Z, M].ask, Ask[Z, N].ask, Ask[Z, O].ask, Ask[Z, P].ask, Ask[Z, Q].ask).tupled)

  implicit def Tuple18Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *], H: Ask[Z, *], I: Ask[Z, *], J: Ask[Z, *], K: Ask[Z, *], L: Ask[Z, *], M: Ask[Z, *], N: Ask[Z, *], O: Ask[Z, *], P: Ask[Z, *], Q: Ask[Z, *], R: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask, Ask[Z, H].ask, Ask[Z, I].ask, Ask[Z, J].ask, Ask[Z, K].ask, Ask[Z, L].ask, Ask[Z, M].ask, Ask[Z, N].ask, Ask[Z, O].ask, Ask[Z, P].ask, Ask[Z, Q].ask, Ask[Z, R].ask).tupled)

  implicit def Tuple19Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *], H: Ask[Z, *], I: Ask[Z, *], J: Ask[Z, *], K: Ask[Z, *], L: Ask[Z, *], M: Ask[Z, *], N: Ask[Z, *], O: Ask[Z, *], P: Ask[Z, *], Q: Ask[Z, *], R: Ask[Z, *], S: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask, Ask[Z, H].ask, Ask[Z, I].ask, Ask[Z, J].ask, Ask[Z, K].ask, Ask[Z, L].ask, Ask[Z, M].ask, Ask[Z, N].ask, Ask[Z, O].ask, Ask[Z, P].ask, Ask[Z, Q].ask, Ask[Z, R].ask, Ask[Z, S].ask).tupled)

  implicit def Tuple20Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *], H: Ask[Z, *], I: Ask[Z, *], J: Ask[Z, *], K: Ask[Z, *], L: Ask[Z, *], M: Ask[Z, *], N: Ask[Z, *], O: Ask[Z, *], P: Ask[Z, *], Q: Ask[Z, *], R: Ask[Z, *], S: Ask[Z, *], T: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask, Ask[Z, H].ask, Ask[Z, I].ask, Ask[Z, J].ask, Ask[Z, K].ask, Ask[Z, L].ask, Ask[Z, M].ask, Ask[Z, N].ask, Ask[Z, O].ask, Ask[Z, P].ask, Ask[Z, Q].ask, Ask[Z, R].ask, Ask[Z, S].ask, Ask[Z, T].ask).tupled)

  implicit def Tuple21Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *], H: Ask[Z, *], I: Ask[Z, *], J: Ask[Z, *], K: Ask[Z, *], L: Ask[Z, *], M: Ask[Z, *], N: Ask[Z, *], O: Ask[Z, *], P: Ask[Z, *], Q: Ask[Z, *], R: Ask[Z, *], S: Ask[Z, *], T: Ask[Z, *], U: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask, Ask[Z, H].ask, Ask[Z, I].ask, Ask[Z, J].ask, Ask[Z, K].ask, Ask[Z, L].ask, Ask[Z, M].ask, Ask[Z, N].ask, Ask[Z, O].ask, Ask[Z, P].ask, Ask[Z, Q].ask, Ask[Z, R].ask, Ask[Z, S].ask, Ask[Z, T].ask, Ask[Z, U].ask).tupled)

  implicit def Tuple22Ask[Z[_]: Monad, A: Ask[Z, *], B: Ask[Z, *], C: Ask[Z, *], D: Ask[Z, *], E: Ask[Z, *], F: Ask[Z, *], G: Ask[Z, *], H: Ask[Z, *], I: Ask[Z, *], J: Ask[Z, *], K: Ask[Z, *], L: Ask[Z, *], M: Ask[Z, *], N: Ask[Z, *], O: Ask[Z, *], P: Ask[Z, *], Q: Ask[Z, *], R: Ask[Z, *], S: Ask[Z, *], T: Ask[Z, *], U: Ask[Z, *], V: Ask[Z, *]]: Ask[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)] =
    Ask.make((Ask[Z, A].ask, Ask[Z, B].ask, Ask[Z, C].ask, Ask[Z, D].ask, Ask[Z, E].ask, Ask[Z, F].ask, Ask[Z, G].ask, Ask[Z, H].ask, Ask[Z, I].ask, Ask[Z, J].ask, Ask[Z, K].ask, Ask[Z, L].ask, Ask[Z, M].ask, Ask[Z, N].ask, Ask[Z, O].ask, Ask[Z, P].ask, Ask[Z, Q].ask, Ask[Z, R].ask, Ask[Z, S].ask, Ask[Z, T].ask, Ask[Z, U].ask, Ask[Z, V].ask).tupled)

}
