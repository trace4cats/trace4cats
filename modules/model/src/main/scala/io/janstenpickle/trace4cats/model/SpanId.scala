package io.janstenpickle.trace4cats.model

import cats.effect.kernel.Sync
import cats.syntax.show._
import cats.{Eq, Show}
import org.apache.commons.codec.binary.Hex

import java.util.concurrent.ThreadLocalRandom
import scala.util.Try

case class SpanId private (value: Array[Byte]) extends AnyVal {
  override def toString: String = show"SpanId($this)"
}

object SpanId {
  val size = 8

  trait Gen[F[_]] {
    def gen: F[SpanId]
  }

  object Gen {
    def apply[F[_]](implicit ev: Gen[F]): Gen[F] = ev

    def from[F[_]](_gen: F[SpanId]): Gen[F] = new Gen[F] {
      def gen: F[SpanId] = _gen
    }

    implicit def threadLocalRandomSpanId[F[_]: Sync]: Gen[F] = Gen.from(Sync[F].delay {
      val array = Array.fill[Byte](size)(0)
      ThreadLocalRandom.current.nextBytes(array)
      SpanId.unsafe(array)
    })
  }

  def gen[F[_]: Gen]: F[SpanId] = Gen[F].gen

  def fromHexString(hex: String): Option[SpanId] =
    Try(Hex.decodeHex(hex)).toOption.flatMap(apply)

  def apply(array: Array[Byte]): Option[SpanId] =
    if (array.length == size) Some(new SpanId(array)) else None

  def unsafe(array: Array[Byte]): SpanId =
    apply(array).getOrElse(
      throw new IllegalArgumentException(s"Expected a byte-array of size $size, got ${array.length}")
    )

  val invalid: SpanId = new SpanId(Array.fill(size)(0))

  implicit val show: Show[SpanId] =
    Show.show(sid => Hex.encodeHexString(sid.value))

  implicit val eq: Eq[SpanId] = Eq.by(_.show)
}
