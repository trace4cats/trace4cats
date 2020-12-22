package io.janstenpickle.trace4cats.sttp.tapir

import cats.Show
import cats.syntax.show._
import io.janstenpickle.trace4cats.base.optics.Getter
import io.janstenpickle.trace4cats.model.SpanStatus
import io.janstenpickle.trace4cats.sttp.common.SttpStatusMapping.statusToSpanStatus
import sttp.model.StatusCode

object TapirStatusMapping {
  def errorShowToInternal[E: Show]: TapirStatusMapping[E] = e => SpanStatus.Internal(e.show)
  def errorMessageToInternal[E <: Throwable]: TapirStatusMapping[E] = e => SpanStatus.Internal(e.getMessage)
  def errorStringToInternal[E]: TapirStatusMapping[E] = e => SpanStatus.Internal(e.toString)

  def errorShowToSpanStatus[E: Show](statusCodeGetter: Getter[E, StatusCode]): TapirStatusMapping[E] =
    e => statusToSpanStatus(e.show, statusCodeGetter.get(e))
  def errorMessageToSpanStatus[E <: Throwable](statusCodeGetter: Getter[E, StatusCode]): TapirStatusMapping[E] =
    e => statusToSpanStatus(e.getMessage, statusCodeGetter.get(e))
  def errorStringToSpanStatus[E](statusCodeGetter: Getter[E, StatusCode]): TapirStatusMapping[E] =
    e => statusToSpanStatus(e.toString, statusCodeGetter.get(e))
}
