package io.janstenpickle.trace4cats.inject.zio

import io.janstenpickle.trace4cats.inject.UnliftProvide
import _root_.zio.{Has, IO, RIO, ZIO}
import cats.Applicative
import cats.mtl.Local
import izumi.reflect.Tag

trait ZIOUnliftProvide {
  implicit def zioHasUnliftProvide[R <: Has[_], E, C: Tag]: UnliftProvide[ZIO[R, E, *], ZIO[R with Has[C], E, *], C] =
    new UnliftProvide[ZIO[R, E, *], ZIO[R with Has[C], E, *], C] {
      def applicative: Applicative[ZIO[R with Has[C], E, *]] = zio.interop.catz.monadErrorInstance

      def ask[C2 >: C]: ZIO[R with Has[C], E, C2] = RIO.access[Has[C]](_.get)

      def local[A](fa: ZIO[R with Has[C], E, A])(f: C => C): ZIO[R with Has[C], E, A] = fa.provideSome(_.update(f))

      def lift[A](fa: ZIO[R, E, A]): ZIO[R with Has[C], E, A] = fa

      def provide[A](r: C)(ga: ZIO[R with Has[C], E, A]): ZIO[R, E, A] = ga.provideSome[R](_.add(r))
    }

  def zioHasLocal[R <: Has[_], E, C: Tag]: Local[ZIO[R with Has[C], E, *], C] = zioHasUnliftProvide

  implicit def zioUnliftProvide[E, R]: UnliftProvide[IO[E, *], ZIO[R, E, *], R] =
    new UnliftProvide[IO[E, *], ZIO[R, E, *], R] {
      def applicative: Applicative[ZIO[R, E, *]] = zio.interop.catz.monadErrorInstance

      def ask[R2 >: R]: ZIO[R, E, R2] = RIO.environment[R2]

      def local[A](fa: ZIO[R, E, A])(f: R => R): ZIO[R, E, A] = fa.provideSome(f)

      def lift[A](fa: IO[E, A]): ZIO[R, E, A] = fa

      def provide[A](r: R)(ga: ZIO[R, E, A]): IO[E, A] = ga.provide(r)
    }

}
