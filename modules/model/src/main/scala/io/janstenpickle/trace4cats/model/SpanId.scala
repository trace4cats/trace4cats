package io.janstenpickle.trace4cats.model

import cats.syntax.functor._
import cats.syntax.show._
import cats.effect.std.Random
import cats.{Eq, Functor, Show}
import org.apache.commons.codec.binary.Hex

import scala.util.Try

case class SpanId private (value: Array[Byte]) extends AnyVal {
  override def toString: String = show"SpanId($this)"
}

object SpanId {
  def apply[F[_]: Functor : Random]: F[SpanId] =
    Random[F].nextBytes(8).map(new SpanId(_))

  def fromHexString(hex: String): Option[SpanId] =
    Try(Hex.decodeHex(hex)).toOption.flatMap(apply)

  def apply(array: Array[Byte]): Option[SpanId] =
    if (array.length == 8) Some(new SpanId(array)) else None

  val invalid: SpanId = new SpanId(Array.fill(8)(0))

  implicit val show: Show[SpanId] =
    Show.show(sid => Hex.encodeHexString(sid.value))

  implicit val eq: Eq[SpanId] = Eq.by(_.show)
}
