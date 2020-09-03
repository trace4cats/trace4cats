package io.janstenpickle.trace4cats.model

import java.util.concurrent.ThreadLocalRandom
import cats.syntax.show._
import cats.{ApplicativeError, Defer, Eq, Show}
import org.apache.commons.codec.binary.Hex

import scala.util.Try

case class TraceId private (value: Array[Byte]) extends AnyVal {
  override def toString: String = show"TraceId($this)"
}

object TraceId {
  def apply[F[_]: Defer: ApplicativeError[*[_], Throwable]]: F[TraceId] =
    Defer[F].defer(ApplicativeError[F, Throwable].catchNonFatal {
      val array: Array[Byte] = Array.fill(16)(0)
      ThreadLocalRandom.current.nextBytes(array)
      new TraceId(array)
    })

  def fromHexString(hex: String): Option[TraceId] =
    Try(Hex.decodeHex(hex)).toOption.flatMap(apply)

  def apply(array: Array[Byte]): Option[TraceId] =
    if (array.length == 16) Some(new TraceId(array)) else None

  val invalid: TraceId = new TraceId(Array.fill(16)(0))

  implicit val show: Show[TraceId] =
    Show.show(tid => Hex.encodeHexString(tid.value))

  implicit val eq: Eq[TraceId] = Eq.by(_.show)
}
