package io.janstenpickle.trace4cats.sttp.common

import cats.Eq
import io.janstenpickle.trace4cats.model.TraceHeaders
import io.janstenpickle.trace4cats.sttp.common.SttpHeaders.converter
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import sttp.model.{Header, Headers}

class SttpHeadersConverterSpec extends AnyFlatSpec with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {
  behavior.of("SttpHeaders.converter")

  it should "convert headers isomorphically" in forAll { traceHeaders: TraceHeaders =>
    assert(Eq.eqv(traceHeaders, converter.from(converter.to(traceHeaders))))
  }

  it should "convert example headers" in {
    val headers = Headers(List(Header("header1", "value1"), Header("header2", "value2")))
    val expected = TraceHeaders.of("header1" -> "value1", "header2" -> "value2")

    assert(Eq.eqv(converter.from(headers), expected))
  }
}
