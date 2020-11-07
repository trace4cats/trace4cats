package io.janstenpickle.trace4cats.model

import cats.kernel.Monoid
import cats.syntax.all._
import cats.{Eq, Foldable, Functor, Show}

case class Batch[F[_]](spans: F[CompletedSpan]) extends AnyVal

object Batch {
  implicit def show[F[_]: Functor: Foldable]: Show[Batch[F]] =
    Show.show { batch =>
      show"""spans:
          |${batch.spans.map(_.show).map(s => s"  $s").mkString_("")}""".stripMargin
    }

  implicit def eq[F[_]](implicit e: Eq[F[CompletedSpan]]): Eq[Batch[F]] = Eq.by(_.spans)

  implicit def monoid[F[_]](implicit m: Monoid[F[CompletedSpan]]): Monoid[Batch[F]] =
    Monoid.instance(Batch(m.empty), (x, y) => Batch(m.combine(x.spans, y.spans)))
}
