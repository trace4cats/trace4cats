package io.janstenpickle.trace4cats.inject

import cats.data.Kleisli
import cats.mtl.Local
import cats.syntax.applicative._
import cats.{Applicative, ~>}

trait UnliftProvideBase
object UnliftProvideBase {
  implicit def klesliInstance[F[_]: Applicative, R]: UnliftProvide[F, Kleisli[F, R, *], R] =
    new KleisliUnliftProvide[F, R]
}

trait Lift[F[_], G[_]] extends UnliftProvideBase {
  def lift[A](fa: F[A]): G[A]
  def liftK: F ~> G = λ[F ~> G](lift(_))
}

trait UnliftProvide[F[_], G[_], R] extends Local[G, R] with Lift[F, G] {
  def provide[A](r: R)(ga: G[A]): F[A]
  def provideK(r: R): G ~> F = λ[G ~> F](provide(r)(_))
}

class KleisliUnliftProvide[F[_] : Applicative, R] extends UnliftProvide[F, Kleisli[F, R, *], R] { self =>
  val applicative: Applicative[Kleisli[F, R, *]] = implicitly
  def ask[E >: R]: Kleisli[F, R, E] = Kleisli[F, R, E](r => (r: E).pure[F])
  def local[A](fa: Kleisli[F, R, A])(f: R => R): Kleisli[F, R, A] = fa.local(f)
  def lift[A](fa: F[A]): Kleisli[F, R, A] = Kleisli.liftF(fa)
  def provide[A](r: R)(ga: Kleisli[F, R, A]): F[A] = ga.run(r)

  def lens[R1](get: R => R1, set: (R, R1) => R): Local[Kleisli[F, R, *], R1] = new Local[Kleisli[F, R, *], R1] {
    val applicative: Applicative[Kleisli[F, R, *]] = self.applicative
    def ask[E1 >: R1]: Kleisli[F, R, E1] = self.ask[R].map(get)
    def local[A](fa: Kleisli[F, R, A])(f: R1 => R1): Kleisli[F, R, A] =
      self.local(fa)(r => set(r, f(get(r))))
  }
}