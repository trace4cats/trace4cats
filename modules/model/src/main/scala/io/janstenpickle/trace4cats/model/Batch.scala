package io.janstenpickle.trace4cats.model

import cats.Show
import cats.implicits._

case class Batch(process: TraceProcess, spans: List[CompletedSpan])

object Batch {
  implicit val show: Show[Batch] = Show.show { batch =>
    show"""process: ${batch.process}
          |spans:
          |${batch.spans.map(_.show.indent(2)).mkString("")}""".stripMargin
  }
}
