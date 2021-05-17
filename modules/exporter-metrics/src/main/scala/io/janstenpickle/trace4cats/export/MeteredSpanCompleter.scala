package io.janstenpickle.trace4cats.`export`

import cats.Show
import cats.data.NonEmptyList
import cats.effect.kernel.Sync
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import io.chrisdavenport.epimetheus._
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.CompletedSpan
import shapeless.Sized

object MeteredSpanCompleter {
  def one[F[_]: Sync, A: Show](
    collectorRegistry: CollectorRegistry[F]
  )(label: A, completer: SpanCompleter[F]): F[SpanCompleter[F]] =
    apply(collectorRegistry)((label, completer)).map(_.head._2)

  def apply[F[_]: Sync, A: Show](
    collectorRegistry: CollectorRegistry[F]
  )(completer: (A, SpanCompleter[F]), others: (A, SpanCompleter[F])*): F[NonEmptyList[(A, SpanCompleter[F])]] = {

    def instrument(
      counter: UnlabelledCounter[F, A],
      errorCounter: UnlabelledCounter[F, A]
    ): (A, SpanCompleter[F]) => SpanCompleter[F] =
      (a, completer) =>
        new SpanCompleter[F] {
          override def complete(span: CompletedSpan.Builder): F[Unit] =
            (completer.complete(span) >> counter.label(a).inc).onError { case _ =>
              errorCounter.label(a).inc
            }
        }

    for {
      spanCounter <- Counter
        .labelled(
          collectorRegistry,
          Name("trace4cats_completer_span_total"),
          "Number of span batches sent via this exporter",
          Sized(Label("completer_name")),
          { a: A => Sized(a.show) }
        )
      errorCounter <- Counter
        .labelled(
          collectorRegistry,
          Name("trace4cats_completer_error_total"),
          "Number of spans that could not be completed",
          Sized(Label("completer_name")),
          { a: A => Sized(a.show) }
        )
    } yield {
      val instrumented = instrument(spanCounter, errorCounter)

      NonEmptyList(completer, others.toList).map { case (a, c) => a -> instrumented(a, c) }
    }
  }

}
