package io.janstenpickle.trace4cats.model

import cats.{Eq, Show}
import cats.syntax.all._

case class TraceProcess(serviceName: String, attributes: Map[String, AttributeValue] = Map.empty)

object TraceProcess {
  implicit val show: Show[TraceProcess] = Show.show { process =>
    show"[ service-name: ${process.serviceName}, attributes: ${process.attributes} ]"
  }

  implicit val eq: Eq[TraceProcess] = magnolify.cats.semiauto.EqDerivation[TraceProcess]
}
