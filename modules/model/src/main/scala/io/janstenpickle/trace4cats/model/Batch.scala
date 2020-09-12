package io.janstenpickle.trace4cats.model

import cats.{Eq, Show}
import cats.syntax.all._

case class Batch(process: TraceProcess, spans: List[CompletedSpan])

object Batch {
  implicit val show: Show[Batch] = Show.show { batch =>
    show"""process: ${batch.process}
          |spans:
          |${batch.spans.map(_.show).map(s => s"  $s").mkString("")}""".stripMargin
  }

  implicit val eq: Eq[Batch] = cats.derived.semi.eq[Batch]
}
