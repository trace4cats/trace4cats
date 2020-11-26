package io.janstenpickle.trace4cats.base.context.zio

import cats.{~>, Monad}
import io.janstenpickle.trace4cats.base.context.{Provide, Unlift}
import izumi.reflect.Tag
import zio.{Has, IO, ZIO}

trait ZIOContextInstances extends ZIOContextInstancesLowPriority {
  implicit def zioProvide[E, R]: Provide[IO[E, *], ZIO[R, E, *], R] =
    new Provide[IO[E, *], ZIO[R, E, *], R] {
      def F: Monad[ZIO[R, E, *]] = zio.interop.catz.monadErrorInstance

      def ask[R2 >: R]: ZIO[R, E, R2] = ZIO.environment

      def local[A](fa: ZIO[R, E, A])(f: R => R): ZIO[R, E, A] = fa.provideSome(f)

      def lift[A](la: IO[E, A]): ZIO[R, E, A] = la

      def provide[A](fa: ZIO[R, E, A])(r: R): IO[E, A] = fa.provide(r)
    }

  implicit def zioProvideSome[R <: Has[_], R1 <: Has[_], E, C: Tag](implicit
    ev1: R1 <:< R with Has[C],
    ev2: R with Has[C] <:< R1
  ): Provide[ZIO[R, E, *], ZIO[R1, E, *], C] =
    new Provide[ZIO[R, E, *], ZIO[R1, E, *], C] {
      def F: Monad[ZIO[R1, E, *]] = zio.interop.catz.monadErrorInstance

      def ask[C2 >: C]: ZIO[R1, E, C2] = ZIO.access[Has[C]](_.get).provideSome(ev1)

      def local[A](fa: ZIO[R1, E, A])(f: C => C): ZIO[R1, E, A] =
        fa.provideSome[R1](_.update(f))

      def lift[A](la: ZIO[R, E, A]): ZIO[R1, E, A] = la.provideSome(ev1)

      def provide[A](fa: ZIO[R1, E, A])(c: C): ZIO[R, E, A] = fa.provideSome[R](_.add(c))
    }

}

trait ZIOContextInstancesLowPriority {
  implicit def zioUnliftSome[R, R1 <: R, E]: Unlift[ZIO[R, E, *], ZIO[R1, E, *]] =
    new Unlift[ZIO[R, E, *], ZIO[R1, E, *]] {
      def F: Monad[ZIO[R1, E, *]] = zio.interop.catz.monadErrorInstance
      def lift[A](la: ZIO[R, E, A]): ZIO[R1, E, A] = la
      def unlift: ZIO[R1, E, ZIO[R1, E, *] ~> ZIO[R, E, *]] =
        ZIO.access[R1](r1 => λ[ZIO[R1, E, *] ~> ZIO[R, E, *]](_.provide(r1)))
    }
}
