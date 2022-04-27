package io.janstenpickle.trace4cats.`export`

import cats.Functor
import cats.effect.kernel.{Ref, Sync}
import cats.syntax.functor._
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.{CompletedSpan, TraceProcess}

import scala.collection.immutable.Queue

/** RefSpanCompleter collects all spans in a queue within an atomic reference. Best used for testing purposes.
  */
class RefSpanCompleter[F[_]](process: TraceProcess, ref: Ref[F, Queue[CompletedSpan]]) extends SpanCompleter[F] {
  override def complete(span: CompletedSpan.Builder): F[Unit] = ref.update(_.enqueue(span.build(process)))
  def get: F[Queue[CompletedSpan]] = ref.get
}

object RefSpanCompleter {
  def apply[F[_]: Functor: Ref.Make](serviceName: String): F[RefSpanCompleter[F]] = apply[F](TraceProcess(serviceName))
  def unsafe[F[_]: Sync](serviceName: String): RefSpanCompleter[F] = unsafe[F](TraceProcess(serviceName))

  def apply[F[_]: Functor: Ref.Make](process: TraceProcess): F[RefSpanCompleter[F]] =
    Ref.of(Queue.empty[CompletedSpan]).map(new RefSpanCompleter(process, _))
  def unsafe[F[_]: Sync](process: TraceProcess): RefSpanCompleter[F] =
    new RefSpanCompleter(process, Ref.unsafe(Queue.empty[CompletedSpan]))
}
