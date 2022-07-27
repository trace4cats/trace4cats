package trace4cats.kernel

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.kernel.laws.discipline.MonoidTests
import cats.{Applicative, Eq, Id}
import trace4cats.model.Batch
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

class SpanExporterSpec extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with FunSuiteDiscipline {
  val batch: Batch[List] = Batch[List](List.empty)

  def spanExporterArb[F[_]: Applicative]: Arbitrary[SpanExporter[F, List]] =
    Arbitrary(Gen.const(SpanExporter.empty[F, List]))
  implicit val idSpanCompleter: Arbitrary[SpanExporter[Id, List]] = spanExporterArb
  implicit val ioSpanCompleter: Arbitrary[SpanExporter[IO, List]] = spanExporterArb

  implicit val idSpanCompleterEq: Eq[SpanExporter[Id, List]] = new Eq[SpanExporter[Id, List]] {
    override def eqv(x: SpanExporter[Id, List], y: SpanExporter[Id, List]): Boolean =
      x.exportBatch(batch) === y.exportBatch(batch)
  }

  implicit val ioSpanCompleterEq: Eq[SpanExporter[IO, List]] = new Eq[SpanExporter[IO, List]] {
    override def eqv(x: SpanExporter[IO, List], y: SpanExporter[IO, List]): Boolean =
      x.exportBatch(batch).unsafeRunSync() === y.exportBatch(batch).unsafeRunSync()
  }

  checkAll("SpanExporter Monoid non-parallel", MonoidTests[SpanExporter[Id, List]].monoid)
  checkAll("SpanExporter Monoid parallel", MonoidTests[SpanExporter[IO, List]].monoid)

}
