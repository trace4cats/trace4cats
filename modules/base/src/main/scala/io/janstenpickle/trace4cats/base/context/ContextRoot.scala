package io.janstenpickle.trace4cats.base.context

import cats.{~>, Monad}
import cats.arrow.FunctionK
import cats.data.Kleisli

trait ContextRoot extends Serializable

object ContextRoot extends ContextRootInstancesLowPriority {
  implicit def kleisliInstance[F[_]: Monad, R]: Provide[F, Kleisli[F, R, *], R] =
    new Provide[F, Kleisli[F, R, *], R] {
      def Low: Monad[F] = implicitly
      def F: Monad[Kleisli[F, R, *]] = implicitly

      def ask[R1 >: R]: Kleisli[F, R, R1] = Kleisli.ask
      def local[A](fa: Kleisli[F, R, A])(f: R => R): Kleisli[F, R, A] = fa.local(f)

      def lift[A](fa: F[A]): Kleisli[F, R, A] = Kleisli.liftF(fa)
      def provide[A](fa: Kleisli[F, R, A])(r: R): F[A] = fa.run(r)
    }
}

private[context] trait ContextRootInstancesLowPriority {
  implicit def idUnlift[F[_]: Monad]: Unlift[F, F] = new Unlift[F, F] {
    def F: Monad[F] = implicitly
    def Low: Monad[F] = implicitly

    def askUnlift: F[F ~> F] = F.pure(FunctionK.id)
    def lift[A](fa: F[A]): F[A] = fa
  }

  implicit def kleisliIdProvide[F[_]: Monad, R]: Provide[Kleisli[F, R, *], Kleisli[F, R, *], R] =
    new Provide[Kleisli[F, R, *], Kleisli[F, R, *], R] {
      def F: Monad[Kleisli[F, R, *]] = implicitly
      def Low: Monad[Kleisli[F, R, *]] = implicitly

      def lift[A](fa: Kleisli[F, R, A]): Kleisli[F, R, A] = fa
      def ask[R1 >: R]: Kleisli[F, R, R1] = Kleisli.ask
      def local[A](fa: Kleisli[F, R, A])(f: R => R): Kleisli[F, R, A] = fa.local(f)
      def provide[A](fa: Kleisli[F, R, A])(r: R): Kleisli[F, R, A] = fa.local(_ => r)
    }
}
