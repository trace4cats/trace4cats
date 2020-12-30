package io.janstenpickle.trace4cats.sttp.tapir.model

import io.circe.generic.auto._
import io.janstenpickle.trace4cats.base.optics.Getter
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

import scala.util.control.NoStackTrace

sealed abstract class ErrorInfoException(msg: String) extends RuntimeException(msg) with NoStackTrace

object ErrorInfoException {
  case class NotFound(what: String) extends ErrorInfoException(s"`$what` not found")
  case class Unauthorized(realm: String) extends ErrorInfoException(s"`$realm` not allowed")
  case class Unknown(code: Int, msg: String) extends ErrorInfoException(s"err=$code msg=$msg")
  case object NoContent extends ErrorInfoException("no content")

  val endpointOutput: EndpointOutput[ErrorInfoException] =
    oneOf[ErrorInfoException](
      statusMapping(StatusCode.NotFound, jsonBody[NotFound]),
      statusMapping(StatusCode.Unauthorized, jsonBody[Unauthorized]),
      statusMapping(StatusCode.NoContent, emptyOutput.map(_ => NoContent)(_ => ())),
      statusDefaultMapping(jsonBody[Unknown])
    )

  val statusCodeGetter: Getter[ErrorInfoException, StatusCode] = {
    case _: NotFound => StatusCode.NotFound
    case _: Unauthorized => StatusCode.Unauthorized
    case NoContent => StatusCode.NoContent
    case _: Unknown => StatusCode.InternalServerError
  }
}
