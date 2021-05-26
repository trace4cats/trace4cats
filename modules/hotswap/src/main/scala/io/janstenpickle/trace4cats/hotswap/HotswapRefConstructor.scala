package io.janstenpickle.trace4cats.hotswap

import cats.effect.kernel.{Resource, Temporal}

/** Use some `I` to construct `R` via a provided function, within a [[HotswapRef]].
  *
  * Calls to [[swapWith]] update the value of `R` within [[HotswapRef]] with provided with some `I`.
  */
trait HotswapRefConstructor[F[_], I, R] extends HotswapRef[F, (I, R)] {
  protected def func: I => Resource[F, R]

  /** Swap `R` using `I` using some function of `I` to [[cats.effect.kernel.Resource]] of `R`, provided at construction
    * time
    */
  def swapWith(next: I): F[Unit] = swap(func(next).map(next -> _))

  /** Access the current input value `I` on its own
    */
  def accessI: Resource[F, I] = access.map(_._1)

  /** Access the current input value `R` on its own
    */
  def accessR: Resource[F, R] = access.map(_._2)
}

object HotswapRefConstructor {

  /** Creates a new [[HotswapRefConstructor]] initialized using `initial` and `make`. The [[HotswapRefConstructor]]
    * instance is returned within a [[cats.effect.kernel.Resource]].
    *
    * @param initial the initial value of input `I` to be used to construct `R`
    * @param make used to construct a [[cats.effect.kernel.Resource]] of `R` from `I`, called on construction and when
    *             [[swapWith]] is used.
    */
  def apply[F[_]: Temporal, I, R](
    initial: I
  )(make: I => Resource[F, R]): Resource[F, HotswapRefConstructor[F, I, R]] = {
    HotswapRef[F, (I, R)](make(initial).map(initial -> _)).map { hotswap =>
      new HotswapRefConstructor[F, I, R] {
        override protected val func: I => Resource[F, R] = make
        override def swap(next: Resource[F, (I, R)]): F[Unit] = hotswap.swap(next)
        override val access: Resource[F, (I, R)] = hotswap.access
      }
    }
  }
}
