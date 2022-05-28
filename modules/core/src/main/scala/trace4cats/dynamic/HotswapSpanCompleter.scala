package trace4cats.dynamic

import cats.effect.kernel.{Resource, Temporal}
import cats.kernel.Eq
import cats.syntax.applicative._
import io.janstenpickle.hotswapref.ConditionalHotswapRefConstructor
import trace4cats.kernel.SpanCompleter
import trace4cats.model.CompletedSpan

trait HotswapSpanCompleter[F[_], A] extends SpanCompleter[F] {
  def update(config: A): F[Boolean]
  def getConfig: F[A]
}

object HotswapSpanCompleter {
  def apply[F[_]: Temporal, A: Eq](
    initialConfig: A
  )(makeCompleter: A => Resource[F, SpanCompleter[F]]): Resource[F, HotswapSpanCompleter[F, A]] =
    ConditionalHotswapRefConstructor[F, A, SpanCompleter[F]](initialConfig)(makeCompleter).map { hotswap =>
      new HotswapSpanCompleter[F, A] {
        override def update(config: A): F[Boolean] = hotswap.maybeSwapWith(config)
        override def getConfig: F[A] = hotswap.accessI.use(_.pure)
        override def complete(span: CompletedSpan.Builder): F[Unit] = hotswap.accessR.use(_.complete(span))
      }
    }
}
