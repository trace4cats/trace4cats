package io.janstenpickle.trace4cats.hotswap

import cats.Applicative
import cats.effect.kernel.{MonadCancel, Resource, Temporal}
import cats.kernel.Eq
import cats.syntax.applicative._
import cats.syntax.eq._
import cats.syntax.flatMap._

/** Adds functionality to [[HotswapRefConstructor]] to check whether input `I` has changed and only if it has, swap
  * the current value of `R` with a new [[cats.effect.kernel.Resource]].
  */
trait ConditionalHotswapRefConstructor[F[_], I, R] extends HotswapRefConstructor[F, I, R] {
  protected implicit def F: MonadCancel[F, Throwable]
  protected implicit def eq: Eq[I]

  /** Conditionally swap `R` using `I`. If `next` matches the current value of `I` then do not swap.
    *
    * Use [[swapWith]] to force reinitialization of `R`.
    *
    * Note that the reference to `I` is relinquished before [[swapWith]] is called, so that we don't get blocked.
    *
    * @return boolean result of conditional swap, `true` if `R` has been swapped `false` if it has not been swapped.
    */
  def maybeSwapWith(next: I): F[Boolean] =
    accessI.use(_.neqv(next).pure).flatTap(Applicative[F].whenA(_)(swapWith(next)))
}

object ConditionalHotswapRefConstructor {

  /** Creates a new [[ConditionalHotswapRefConstructor]] initialized using `initial` and `make`. The
    * [[ConditionalHotswapRefConstructor]] instance is returned within a [[cats.effect.kernel.Resource]].
    *
    * A [[cats.kernel.Eq]] instance for `I` is required for [[maybeSwapWith]] to compare the current and next values
    * of `I`.
    *
    * @param initial the initial value of input `I` to be used to construct `R`
    * @param make used to construct a [[cats.effect.kernel.Resource]] of `R` from `I`, called on construction and when
    *             [[swapWith]] or [[maybeSwapWith]] is used.
    */
  def apply[F[_]: Temporal, I: Eq, R](
    initial: I
  )(make: I => Resource[F, R]): Resource[F, ConditionalHotswapRefConstructor[F, I, R]] =
    HotswapRefConstructor[F, I, R](initial)(make).map { hotswap =>
      new ConditionalHotswapRefConstructor[F, I, R] {
        override protected val F: MonadCancel[F, Throwable] = implicitly
        override protected val eq: Eq[I] = implicitly
        override protected val func: I => Resource[F, R] = make
        override def swap(next: Resource[F, (I, R)]): F[Unit] = hotswap.swap(next)
        override val access: Resource[F, (I, R)] = hotswap.access
      }
    }
}
