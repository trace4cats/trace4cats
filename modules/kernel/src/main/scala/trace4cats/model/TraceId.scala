package trace4cats.model

import cats.effect.kernel.Sync
import cats.effect.std.Random
import cats.syntax.functor._
import cats.syntax.show._
import cats.{Eq, Functor, Show}
import org.apache.commons.codec.binary.Hex

import scala.util.Try

case class TraceId private (value: Array[Byte]) extends AnyVal {
  override def toString: String = show"TraceId($this)"
}

object TraceId {
  val size = 16

  trait Gen[F[_]] {
    def gen: F[TraceId]
  }

  object Gen extends GenInstances0 {
    def apply[F[_]](implicit ev: Gen[F]): Gen[F] = ev

    def from[F[_]](f: F[TraceId]): Gen[F] = new Gen[F] {
      def gen: F[TraceId] = f
    }
  }

  trait GenInstances0 extends GenInstances1 {
    implicit def fromRandomTraceIdGen[F[_]: Functor: Random]: Gen[F] =
      Gen.from(Random[F].nextBytes(size).map(TraceId.unsafe))
  }

  trait GenInstances1 { this: GenInstances0 =>
    implicit def threadLocalRandomTraceIdGen[F[_]: Sync]: Gen[F] = {
      implicit val rnd: Random[F] = Random.javaUtilConcurrentThreadLocalRandom[F]
      fromRandomTraceIdGen[F]
    }
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
