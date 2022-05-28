package trace4cats.model

import cats.effect.kernel.Sync
import cats.effect.std.Random
import cats.syntax.functor._
import cats.syntax.show._
import cats.{Eq, Functor, Show}
import org.apache.commons.codec.binary.Hex

import scala.util.Try

case class SpanId private (value: Array[Byte]) extends AnyVal {
  override def toString: String = show"SpanId($this)"
}

object SpanId {
  val size = 8

  trait Gen[F[_]] {
    def gen: F[SpanId]
  }

  object Gen extends GenInstances0 {
    def apply[F[_]](implicit ev: Gen[F]): Gen[F] = ev

    def from[F[_]](f: F[SpanId]): Gen[F] = new Gen[F] {
      def gen: F[SpanId] = f
    }
  }

  trait GenInstances0 extends GenInstances1 {
    implicit def fromRandomSpanIdGen[F[_]: Functor: Random]: Gen[F] =
      Gen.from(Random[F].nextBytes(size).map(SpanId.unsafe))
  }

  trait GenInstances1 { this: GenInstances0 =>
    implicit def threadLocalRandomSpanIdGen[F[_]: Sync]: Gen[F] = {
      implicit val rnd: Random[F] = Random.javaUtilConcurrentThreadLocalRandom[F]
      fromRandomSpanIdGen[F]
    }
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
