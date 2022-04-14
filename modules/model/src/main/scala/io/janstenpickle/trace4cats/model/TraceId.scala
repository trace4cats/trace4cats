package io.janstenpickle.trace4cats.model

import cats.effect.kernel.Sync
import cats.syntax.show._
import cats.{Eq, Show}
import org.apache.commons.codec.binary.Hex

import java.util.concurrent.ThreadLocalRandom
import scala.util.Try

case class TraceId private (value: Array[Byte]) extends AnyVal {
  override def toString: String = show"TraceId($this)"
}

object TraceId {
  val size = 16

  trait Gen[F[_]] {
    def gen: F[TraceId]
  }

  object Gen {
    def apply[F[_]](implicit ev: Gen[F]): Gen[F] = ev

    def from[F[_]](f: F[TraceId]): Gen[F] = new Gen[F] {
      def gen: F[TraceId] = f
    }

    implicit def threadLocalRandomTraceId[F[_]: Sync]: Gen[F] = Gen.from(Sync[F].delay {
      val array = Array.fill[Byte](size)(0)
      ThreadLocalRandom.current.nextBytes(array)
      TraceId.unsafe(array)
    })
  }

  def gen[F[_]: Gen]: F[TraceId] = Gen[F].gen

  def fromHexString(hex: String): Option[TraceId] =
    Try(Hex.decodeHex(hex)).toOption.flatMap(apply)

  def apply(array: Array[Byte]): Option[TraceId] =
    if (array.length == size) Some(new TraceId(array)) else None

  def unsafe(array: Array[Byte]): TraceId =
    apply(array).getOrElse(
      throw new IllegalArgumentException(s"Expected a byte-array of size $size, got ${array.length}")
    )

  val invalid: TraceId = new TraceId(Array.fill(size)(0))

  implicit val show: Show[TraceId] =
    Show.show(tid => Hex.encodeHexString(tid.value))

  implicit val eq: Eq[TraceId] = Eq.by(_.show)
}
