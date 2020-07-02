package io.janstenpickle.trace4cats.kernel

import cats.effect.IO
import cats.kernel.laws.discipline.MonoidTests
import cats.{Applicative, Eq, Id}
import io.janstenpickle.trace4cats.model.CompletedSpan
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

class SpanCompleterSpec extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with FunSuiteDiscipline {
  val completedSpan: CompletedSpan = null

  def spanCompleterArb[F[_]: Applicative]: Arbitrary[SpanCompleter[F]] =
    Arbitrary(Gen.const(SpanCompleter.empty[F]))
  implicit val idSpanCompleter: Arbitrary[SpanCompleter[Id]] = spanCompleterArb
  implicit val ioSpanCompleter: Arbitrary[SpanCompleter[IO]] = spanCompleterArb

  implicit val idSpanCompleterEq: Eq[SpanCompleter[Id]] = new Eq[SpanCompleter[Id]] {
    override def eqv(x: SpanCompleter[Id], y: SpanCompleter[Id]): Boolean =
      x.complete(completedSpan) === y.complete(completedSpan)
  }

  implicit val ioSpanCompleterEq: Eq[SpanCompleter[IO]] = new Eq[SpanCompleter[IO]] {
    override def eqv(x: SpanCompleter[IO], y: SpanCompleter[IO]): Boolean =
      x.complete(completedSpan).unsafeRunSync() === y.complete(completedSpan).unsafeRunSync()
  }

  checkAll("SpanCompleter Monoid non-parallel", MonoidTests[SpanCompleter[Id]].monoid)
  checkAll("SpanCompleter Monoid parallel", MonoidTests[SpanCompleter[IO]].monoid)

}
