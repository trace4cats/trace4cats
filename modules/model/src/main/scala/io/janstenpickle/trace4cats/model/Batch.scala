package io.janstenpickle.trace4cats.model

import cats.kernel.Monoid
import cats.{Eq, Show}
import cats.syntax.all._

case class Batch(spans: List[CompletedSpan])

object Batch {
  def apply(process: TraceProcess, spans: List[CompletedSpan]): Batch =
    Batch(spans.map { span =>
      span.copy(attributes = span.attributes ++ process.attributes)
    })

  implicit val show: Show[Batch] = Show.show { batch =>
    show"""spans:
          |${batch.spans.map(_.show).map(s => s"  $s").mkString("")}""".stripMargin
  }

  implicit val eq: Eq[Batch] = cats.derived.semiauto.eq[Batch]

  implicit val monoid: Monoid[Batch] = cats.derived.semiauto.monoid[Batch]
}
