package io.janstenpickle.trace4cats.collector

import cats.instances.list._
import cats.syntax.foldable._
import cats.{Applicative, Parallel}
import fs2.Pipe
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.Batch

object Sink {
  def apply[F[_]: Applicative: Parallel](completers: SpanCompleter[F]*): Pipe[F, Batch, Unit] = {
    val combined = completers.toList.combineAll

    _.evalMap(combined.completeBatch)
  }
}
