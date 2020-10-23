package io.janstenpickle.trace4cats

import cats.Eq
import cats.effect.IO
import cats.kernel.laws.discipline.SemigroupTests
import io.janstenpickle.trace4cats.model.SpanContext
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.typelevel.discipline.scalatest.FunSuiteDiscipline

class ToHeadersSpec extends AnyFunSuite with ScalaCheckDrivenPropertyChecks with FunSuiteDiscipline {
  val parentContext: SpanContext = SpanContext.root[IO].unsafeRunSync()
  val context: SpanContext = SpanContext.child[IO](parentContext).unsafeRunSync()

  implicit val toHeadersArb: Arbitrary[ToHeaders] = Arbitrary(
    Gen.oneOf(ToHeaders.w3c, ToHeaders.b3, ToHeaders.b3Single, ToHeaders.envoy)
  )

  implicit val toHeadersEq: Eq[ToHeaders] = Eq.instance { (x, y) =>
    val headers = x.fromContext(context)
    Eq.eqv(headers, y.fromContext(context)) && Eq.eqv(x.toContext(headers), y.toContext(headers))
  }

  checkAll("ToHeaders Semigroup", SemigroupTests[ToHeaders].semigroup)
}
