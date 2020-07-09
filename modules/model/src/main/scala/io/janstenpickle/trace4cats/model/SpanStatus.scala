package io.janstenpickle.trace4cats.model

import enumeratum.EnumEntry.Camelcase
import enumeratum._

sealed trait SpanStatus extends EnumEntry {
  def canonicalCode: Int
  def isOk: Boolean
}
object SpanStatus extends Enum[SpanStatus] with CatsEnum[SpanStatus] {
  override def values = findValues

  case object Ok extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 0
    override lazy val isOk: Boolean = true
  }
  case object Cancelled extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 1
    override lazy val isOk: Boolean = false
  }
  case object Unknown extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 2
    override lazy val isOk: Boolean = false
  }
  case object InvalidArgument extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 3
    override lazy val isOk: Boolean = false
  }
  case object DeadlineExceeded extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 4
    override lazy val isOk: Boolean = false
  }
  case object NotFound extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 5
    override lazy val isOk: Boolean = false
  }
  case object AlreadyExists extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 6
    override lazy val isOk: Boolean = false
  }
  case object PermissionDenied extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 7
    override lazy val isOk: Boolean = false
  }
  case object ResourceExhausted extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 8
    override lazy val isOk: Boolean = false
  }
  case object FailedPrecondition extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 9
    override lazy val isOk: Boolean = false
  }
  case object Aborted extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 10
    override lazy val isOk: Boolean = false
  }
  case object OutOfRange extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 11
    override lazy val isOk: Boolean = false
  }
  case object Unimplemented extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 12
    override lazy val isOk: Boolean = false
  }
  case class Internal(message: String) extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 13
    override lazy val isOk: Boolean = false
  }
  case object Unavailable extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 14
    override lazy val isOk: Boolean = false
  }
  case object DataLoss extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 15
    override lazy val isOk: Boolean = false
  }
  case object Unauthenticated extends SpanStatus with Camelcase {
    override lazy val canonicalCode: Int = 16
    override lazy val isOk: Boolean = false
  }
}
