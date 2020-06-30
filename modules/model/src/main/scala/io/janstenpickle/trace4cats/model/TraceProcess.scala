package io.janstenpickle.trace4cats.model

import cats.Show
import cats.implicits._

case class TraceProcess(serviceName: String, attributes: Map[String, TraceValue] = Map.empty)

object TraceProcess {
  implicit val show: Show[TraceProcess] = Show.show { process =>
    show"[ service-name: ${process.serviceName}, attributes: ${process.attributes} ]"
  }
}
