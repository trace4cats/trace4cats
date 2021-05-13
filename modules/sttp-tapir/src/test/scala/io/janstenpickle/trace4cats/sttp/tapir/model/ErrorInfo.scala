package io.janstenpickle.trace4cats.sttp.tapir.model

import cats.Show
import io.circe.generic.auto._
import io.janstenpickle.trace4cats.base.optics.Getter
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

sealed trait ErrorInfo

object ErrorInfo {
  case class NotFound(what: String) extends ErrorInfo
  case class Unauthorized(realm: String) extends ErrorInfo
  case class Unknown(code: Int, msg: String) extends ErrorInfo
  case object NoContent extends ErrorInfo

  implicit val show: Show[ErrorInfo] = cats.derived.semiauto.show

  val endpointOutput: EndpointOutput[ErrorInfo] =
    oneOf[ErrorInfo](
      oneOfMapping(StatusCode.NotFound, jsonBody[NotFound]),
      oneOfMapping(StatusCode.Unauthorized, jsonBody[Unauthorized]),
      oneOfMapping(StatusCode.NoContent, emptyOutput.map(_ => NoContent)(_ => ())),
      oneOfDefaultMapping(jsonBody[Unknown])
    )

  val statusCodeGetter: Getter[ErrorInfo, StatusCode] = {
    case _: NotFound => StatusCode.NotFound
    case _: Unauthorized => StatusCode.Unauthorized
    case NoContent => StatusCode.NoContent
    case _: Unknown => StatusCode.InternalServerError
  }
}
