package io.janstenpickle.trace4cats.sttp.common

import io.janstenpickle.trace4cats.model.SpanStatus
import sttp.model.StatusCode

object SttpStatusMapping {
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
