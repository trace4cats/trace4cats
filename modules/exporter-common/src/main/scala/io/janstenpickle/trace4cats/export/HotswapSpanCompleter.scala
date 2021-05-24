package io.janstenpickle.trace4cats.`export`

import cats.Applicative
import cats.effect.kernel.{Concurrent, Ref, Resource}
import cats.effect.std.Hotswap
import cats.kernel.Eq
import cats.syntax.eq._
import cats.syntax.flatMap._
import cats.syntax.functor._
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.CompletedSpan

trait HotswapSpanCompleter[F[_], A] extends SpanCompleter[F] {
  def update(id: A, completer: Resource[F, SpanCompleter[F]]): F[Boolean]
}

object HotswapSpanCompleter {

  def apply[F[_]: Concurrent, A: Eq](
    initialId: A,
    initialCompleter: Resource[F, SpanCompleter[F]]
  ): Resource[F, HotswapSpanCompleter[F, A]] = {
    val noopCompleter: Resource[F, SpanCompleter[F]] = Resource.pure(SpanCompleter.empty)

    // Keep two hotswap resources for the underlying completers and a ref to indicate which one to use.
    // This is so that when the completer config is swapped, the remaining queued spans are flushed to
    // the exporter, but new spans will be sent to the new completer and not blocked while the old completer
    // is torn down. The `update` method will block until the completer is torn down, however.
    // Introduces the risk of us leaking resources in order to make updating the completer smooth
    for {
      (hs0, initialCompleter) <- Hotswap(initialCompleter)
      (hs1, _) <- Hotswap(noopCompleter)
      configRef <- Resource.eval(
        Ref.of[F, (A, Either[SpanCompleter[F], SpanCompleter[F]])](initialId -> Right(initialCompleter))
      )
    } yield new HotswapSpanCompleter[F, A] {
      override def update(newId: A, completer: Resource[F, SpanCompleter[F]]): F[Boolean] = configRef.get.flatMap {
        case (currentConfig, activeCompleter) =>
          val updated = currentConfig.neqv(newId)

          (updated, activeCompleter) match {
            case (true, Right(_)) =>
              hs1.swap(completer).flatMap(newCompleter => configRef.set(newId -> Left(newCompleter))) >> hs0
                .swap(noopCompleter)
                .as(updated)
            case (true, Left(_)) =>
              hs0.swap(completer).flatMap(newCompleter => configRef.set(newId -> Right(newCompleter))) >> hs1
                .swap(noopCompleter)
                .as(updated)
            case (false, _) => Applicative[F].pure(updated)
          }
      }

      override def complete(span: CompletedSpan.Builder): F[Unit] =
        configRef.get.flatMap(_._2.merge.complete(span))
    }
  }
}
