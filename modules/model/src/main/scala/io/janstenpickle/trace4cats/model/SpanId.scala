package io.janstenpickle.trace4cats.model

import java.util.concurrent.ThreadLocalRandom

import cats.{ApplicativeError, Defer, Show}
import org.apache.commons.codec.binary.Hex

import scala.util.Try

case class SpanId private (value: Array[Byte]) extends AnyVal {
  override def toString: String = Hex.encodeHexString(value)
}

object SpanId {
  def apply[F[_]: Defer: ApplicativeError[*[_], Throwable]]: F[SpanId] =
    Defer[F].defer(ApplicativeError[F, Throwable].catchNonFatal {
      val array: Array[Byte] = Array.fill(8)(0)
      ThreadLocalRandom.current.nextBytes(array)
      new SpanId(array)
    })

  def fromHexString(hex: String): Option[SpanId] =
    Try(Hex.decodeHex(hex)).toOption.flatMap(apply)

  def apply(array: Array[Byte]): Option[SpanId] =
    if (array.length == 8) Some(new SpanId(array)) else None

  implicit val show: Show[SpanId] =
    Show.show(sid => Hex.encodeHexString(sid.value))
}
