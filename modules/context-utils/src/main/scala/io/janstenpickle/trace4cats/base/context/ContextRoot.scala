package io.janstenpickle.trace4cats.base.context

import cats.arrow.FunctionK
import cats.{~>, Monad}
import cats.data.Kleisli
import cats.syntax.applicative._

trait ContextRoot extends Serializable

object ContextRoot {
  implicit def kleisliInstance[F[_]: Monad, R]: Provide[F, Kleisli[F, R, *], R] =
    new Provide[F, Kleisli[F, R, *], R] {
      val Low: Monad[F] = implicitly
      val F: Monad[Kleisli[F, R, *]] = implicitly

      def ask[R1 >: R]: Kleisli[F, R, R1] = Kleisli.ask
      def local[A](fa: Kleisli[F, R, A])(f: R => R): Kleisli[F, R, A] = fa.local(f)
      def lift[A](fa: F[A]): Kleisli[F, R, A] = Kleisli.liftF(fa)
      def provide[A](fa: Kleisli[F, R, A])(r: R): F[A] = fa.run(r)
    }

  implicit def idInstance[F[_]: Monad]: Unlift[F, F] = new Unlift[F, F] {
    val Low: Monad[F] = implicitly
    val F: Monad[F] = implicitly

    def lift[A](la: F[A]): F[A] = la
    def askUnlift: F[F ~> F] = FunctionK.id[F].pure[F]
  }
}
