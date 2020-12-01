package io.janstenpickle.trace4cats.sttp.client

import io.janstenpickle.trace4cats.model.SpanStatus
import sttp.client.HttpError
import sttp.model.StatusCode

object SttpStatusMapping {
  val errorToSpanStatus: HttpError => SpanStatus = error => statusToSpanStatus(error.body, error.statusCode)

  val statusToSpanStatus: (String, StatusCode) => SpanStatus = {
    case (body, StatusCode.BadRequest) => SpanStatus.Internal(body)
    case (_, StatusCode.Unauthorized) => SpanStatus.Unauthenticated
    case (_, StatusCode.Forbidden) => SpanStatus.PermissionDenied
    case (_, StatusCode.NotFound) => SpanStatus.NotFound
    case (_, StatusCode.TooManyRequests) => SpanStatus.Unavailable
    case (_, StatusCode.BadGateway) => SpanStatus.Unavailable
    case (_, StatusCode.ServiceUnavailable) => SpanStatus.Unavailable
    case (_, StatusCode.GatewayTimeout) => SpanStatus.Unavailable
    case (_, statusCode) if statusCode.isSuccess => SpanStatus.Ok
    case _ => SpanStatus.Unknown
  }
}
