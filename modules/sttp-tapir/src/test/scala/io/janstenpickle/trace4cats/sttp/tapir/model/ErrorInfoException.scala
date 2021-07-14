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
      oneOfMapping(StatusCode.NotFound, jsonBody[NotFound]),
      oneOfMapping(StatusCode.Unauthorized, jsonBody[Unauthorized]),
      oneOfMapping(StatusCode.NoContent, emptyOutput.map(_ => NoContent)(_ => ())),
      oneOfDefaultMapping(jsonBody[Unknown])
    )

  val statusCodeGetter: Getter[ErrorInfoException, StatusCode] = {
    case _: NotFound => StatusCode.NotFound
    case _: Unauthorized => StatusCode.Unauthorized
    case NoContent => StatusCode.NoContent
    case _: Unknown => StatusCode.InternalServerError
  }
}
