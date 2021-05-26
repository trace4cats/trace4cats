package io.janstenpickle.trace4cats.hotswap

import cats.effect.kernel.{Clock, Ref, Resource, Temporal}
import cats.effect.std.{Hotswap, Semaphore}
import cats.syntax.functor._
import cats.syntax.monad._

import scala.concurrent.duration._

/** A concurrent data structure that wraps a [[cats.effect.std.Hotswap]] providing access to `R` using a
  * [[cats.effect.kernel.Ref]], that is set on resource acquisition, while providing asynchronous hotswap functionality
  * via calls to [[swap]].
  *
  * In short, calls to [[swap]] do not block the usage of `R` via calls to [[access]].
  *
  * Repeated concurrent calls to [[swap]] are ordered by a semaphore to ensure that `R` doesn't churn unexpectedly.
  * As with calls to [[swap]] on [[cats.effect.std.Hotswap]], [[swap]] will block until the previous
  * [[cats.effect.kernel.Resource]] is finalized. Additionally open references to `R` are counted when it is accessed
  * via [[access]], any `R` with open references will block at finalization until all references are released, and
  * therefore subsequent calls to [[swap]] will block.
  */
trait HotswapRef[F[_], R] {

  /** Swap the current resource with a new version
    *
    * This makes use of [[evalTap]] on the provided [[cats.effect.kernel.Resource]] to ensure the
    * [[cats.effect.kernel.Ref]] with `R` is updated immediately on allocation and may be used by [[access]] calls while
    * [[swap]] blocks, waiting for the previous [[cats.effect.kernel.Resource]] to finalize.
    *
    * This means that while there is previous no finalization process in progress when this is called, `R` may be
    * swapped in the ref, but will block until all references to `R` are removed and `R` is torn down.
    *
    * A semaphore guarantees that concurrent access to [[swap]] will wait while previous resources are finalized.
    */
  def swap(next: Resource[F, R]): F[Unit]

  /** Access `R` safely
    *
    * Note that [[cats.effect.kernel.Resource]] `R` here is to maintain a count of the number of
    * references that are currently open. A resource `R` with open references cannot be finalized and therefore
    * cannot be fully swapped.
    */
  def access: Resource[F, R]
}

object HotswapRef {

  /** Creates a new [[HotswapRef]] initialized with the specified resource. The [[HotswapRef]] instance is returned
    * within a [[cats.effect.kernel.Resource]]
    */
  def apply[F[_]: Temporal, R](initial: Resource[F, R]): Resource[F, HotswapRef[F, R]] = {

    /** Provision a [[cats.effect.kernel.Ref]] to count the number of active requests for `R`, and do not release
      * the resource until the number of open requests reaches 0. This effectively blocks the
      * `swap` method until the previous resource is no longer in use anywhere.
      */
    def makeResource(r: Resource[F, R]): Resource[F, (Ref[F, Long], R)] =
      for {
        r0 <- r
        ref <- Resource.make(Ref.of(0L))(references => Clock[F].sleep(100.millis).whileM_(references.get.map(_ != 0)))
      } yield ref -> r0

    for {
      (hotswap, r) <- Hotswap(makeResource(initial))
      sem <- Resource.eval(Semaphore(1))
      ref <- Resource.eval(Ref.of(r))
    } yield new HotswapRef[F, R] {
      override def swap(next: Resource[F, R]): F[Unit] =
        sem.permit.use(_ => hotswap.swap(makeResource(next).evalTap(ref.set))).void

      override val access: Resource[F, R] =
        for {
          (references, r) <- Resource.eval(ref.get)
          _ <- Resource.make(references.update(_ + 1))(_ => references.update(_ - 1))
        } yield r
    }
  }
}
