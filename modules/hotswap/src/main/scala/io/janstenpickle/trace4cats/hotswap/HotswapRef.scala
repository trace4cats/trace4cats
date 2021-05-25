package io.janstenpickle.trace4cats.hotswap

import cats.effect.kernel._
import cats.effect.std.{Hotswap, Semaphore}
import cats.syntax.functor._
import cats.syntax.monad._

import scala.concurrent.duration._

trait HotswapRef[F[_], R] {
  def swap(r: Resource[F, R]): F[Unit]
  def get: Resource[F, R]
}

object HotswapRef {
  def apply[F[_]: Temporal, R](initial: Resource[F, R]): Resource[F, HotswapRef[F, R]] = {
    // Provision a `Ref` to count the number of active requests for `R`, and do not release
    // the resource until the number of open requests reaches 0. This effectively blocks the
    // `swap` method until the previous resource is no longer in use anywhere.
    def makeResource(r: Resource[F, R]): Resource[F, (Ref[F, Long], R)] =
      for {
        r0 <- r
        ref <- Resource.make(Ref.of(0L))(handles => Clock[F].sleep(100.millis).whileM_(handles.get.map(_ != 0)))
      } yield ref -> r0

    for {
      (hotswap, r) <- Hotswap(makeResource(initial))
      sem <- Resource.eval(Semaphore(1))
      ref <- Resource.eval(Ref.of(r))
    } yield new HotswapRef[F, R] {
      // Note the use of `evalTap` after the `make` call, this ensures the `Ref` with `R` is updated
      // immediately on allocation and may be used by `get` calls while `swap` blocks, waiting for the
      // previous `Resource` to finalize
      // The semaphore guarantees that concurrent access to `swap` will wait while previous resources
      // are finalized.
      override def swap(r: Resource[F, R]): F[Unit] =
        sem.permit.use(_ => hotswap.swap(makeResource(r).evalTap(ref.set))).void

      override def get: Resource[F, R] =
        for {
          (handles, r) <- Resource.eval(ref.get)
          _ <- Resource.make(handles.update(_ + 1))(_ => handles.update(_ - 1))
        } yield r
    }
  }
}
