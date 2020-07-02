package io.janstenpickle.trace4cats.kernel

import cats.effect.IO
import cats.kernel.laws.discipline.MonoidTests
import cats.{Applicative, Eq, Id}
import io.janstenpickle.trace4cats.model.Batch
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

class SpanExporterSpec extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with FunSuiteDiscipline {
  val batch: Batch = null

  def spanExporterArb[F[_]: Applicative]: Arbitrary[SpanExporter[F]] =
    Arbitrary(Gen.const(SpanExporter.empty[F]))
  implicit val idSpanCompleter: Arbitrary[SpanExporter[Id]] = spanExporterArb
  implicit val ioSpanCompleter: Arbitrary[SpanExporter[IO]] = spanExporterArb

  implicit val idSpanCompleterEq: Eq[SpanExporter[Id]] = new Eq[SpanExporter[Id]] {
    override def eqv(x: SpanExporter[Id], y: SpanExporter[Id]): Boolean =
      x.exportBatch(batch) === y.exportBatch(batch)
  }

  implicit val ioSpanCompleterEq: Eq[SpanExporter[IO]] = new Eq[SpanExporter[IO]] {
    override def eqv(x: SpanExporter[IO], y: SpanExporter[IO]): Boolean =
      x.exportBatch(batch).unsafeRunSync() === y.exportBatch(batch).unsafeRunSync()
  }

  checkAll("SpanExporter Monoid non-parallel", MonoidTests[SpanExporter[Id]].monoid)
  checkAll("SpanExporter Monoid parallel", MonoidTests[SpanExporter[IO]].monoid)

}
