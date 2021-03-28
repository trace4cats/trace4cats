package io.janstenpickle.trace4cats.http4s.common

import cats.Eq
import io.janstenpickle.trace4cats.http4s.common.Http4sHeaders.converter
import io.janstenpickle.trace4cats.model.TraceHeaders
import io.janstenpickle.trace4cats.test.ArbitraryInstances
import org.http4s.Headers
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class Http4sHeadersConverterSpec extends AnyFlatSpec with ScalaCheckDrivenPropertyChecks with ArbitraryInstances {
  behavior.of("Http4sHeaders.converter")

  it should "convert headers isomorphically" in forAll { traceHeaders: TraceHeaders =>
    assert(Eq.eqv(traceHeaders, converter.from(converter.to(traceHeaders))))
  }

  it should "convert example headers" in {
    val headers = Headers("header1" -> "value1", "header2" -> "value2")
    val expected = TraceHeaders.of("header1" -> "value1", "header2" -> "value2")

    assert(Eq.eqv(converter.from(headers), expected))
  }
}
