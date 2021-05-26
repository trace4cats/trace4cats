package io.janstenpickle.trace4cats.model

import cats.{Eq, Show}

sealed abstract class SpanStatus(val entryName: String) {
  def canonicalCode: Int
  def isOk: Boolean
}
object SpanStatus {
  case object Ok extends SpanStatus("Ok") {
    override lazy val canonicalCode: Int = 0
    override lazy val isOk: Boolean = true
  }
  case object Cancelled extends SpanStatus("Cancelled") {
    override lazy val canonicalCode: Int = 1
    override lazy val isOk: Boolean = false
  }
  case object Unknown extends SpanStatus("Unknown") {
    override lazy val canonicalCode: Int = 2
    override lazy val isOk: Boolean = false
  }
  case object InvalidArgument extends SpanStatus("InvalidArgument") {
    override lazy val canonicalCode: Int = 3
    override lazy val isOk: Boolean = false
  }
  case object DeadlineExceeded extends SpanStatus("DeadlineExceeded") {
    override lazy val canonicalCode: Int = 4
    override lazy val isOk: Boolean = false
  }
  case object NotFound extends SpanStatus("NotFound") {
    override lazy val canonicalCode: Int = 5
    override lazy val isOk: Boolean = false
  }
  case object AlreadyExists extends SpanStatus("AlreadyExists") {
    override lazy val canonicalCode: Int = 6
    override lazy val isOk: Boolean = false
  }
  case object PermissionDenied extends SpanStatus("PermissionDenied") {
    override lazy val canonicalCode: Int = 7
    override lazy val isOk: Boolean = false
  }
  case object ResourceExhausted extends SpanStatus("ResourceExhausted") {
    override lazy val canonicalCode: Int = 8
    override lazy val isOk: Boolean = false
  }
  case object FailedPrecondition extends SpanStatus("FailedPrecondition") {
    override lazy val canonicalCode: Int = 9
    override lazy val isOk: Boolean = false
  }
  case object Aborted extends SpanStatus("Aborted") {
    override lazy val canonicalCode: Int = 10
    override lazy val isOk: Boolean = false
  }
  case object OutOfRange extends SpanStatus("OutOfRange") {
    override lazy val canonicalCode: Int = 11
    override lazy val isOk: Boolean = false
  }
  case object Unimplemented extends SpanStatus("Unimplemented") {
    override lazy val canonicalCode: Int = 12
    override lazy val isOk: Boolean = false
  }
  case class Internal(message: String) extends SpanStatus("Internal") {
    override lazy val canonicalCode: Int = 13
    override lazy val isOk: Boolean = false
  }
  case object Unavailable extends SpanStatus("Unavailable") {
    override lazy val canonicalCode: Int = 14
    override lazy val isOk: Boolean = false
  }
  case object DataLoss extends SpanStatus("DataLoss") {
    override lazy val canonicalCode: Int = 15
    override lazy val isOk: Boolean = false
  }
  case object Unauthenticated extends SpanStatus("Unauthenticated") {
    override lazy val canonicalCode: Int = 16
    override lazy val isOk: Boolean = false
  }

  implicit val eq: Eq[SpanStatus] = Eq.by {
    case s @ Internal(msg) => (s.canonicalCode, Some(msg))
    case s => (s.canonicalCode, None)
  }
  implicit val show: Show[SpanStatus] = Show.show {
    case s @ Internal(msg) => s"${s.entryName}($msg)"
    case s => s.entryName
  }
}
