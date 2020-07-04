package io.janstenpickle.trace4cats.test

import io.janstenpickle.trace4cats.model.{Batch, CompletedSpan, SpanId, TraceId, TraceProcess, TraceState}
import org.scalacheck.{Arbitrary, Gen, ScalacheckShapeless}

trait ArbitraryInstances extends ScalacheckShapeless {
  private def byteArray(length: Int) = Gen.listOfN(length, Arbitrary.arbByte.arbitrary).map(_.toArray)

  implicit val stringArb: Arbitrary[String] = Arbitrary(for {
    size <- Gen.choose(1, 5)
    chars <- Gen.listOfN(size, Gen.alphaNumChar)
  } yield new String(chars.toArray))

  implicit val spanIdArb: Arbitrary[SpanId] = Arbitrary(byteArray(8).map(SpanId(_).get))
  implicit val traceIdArb: Arbitrary[TraceId] = Arbitrary(byteArray(16).map(TraceId(_).get))

  implicit val traceStateKeyArb: Arbitrary[TraceState.Key] = Arbitrary(
    Gen.alphaLowerStr.suchThat(_.nonEmpty).map(TraceState.Key(_).get)
  )
  implicit val traceStateValueArb: Arbitrary[TraceState.Value] = Arbitrary(
    Gen.alphaNumStr.suchThat(_.nonEmpty).suchThat(_.length < 256).map(TraceState.Value(_).get)
  )
  implicit val traceStateArb: Arbitrary[TraceState] = Arbitrary(
    Gen
      .listOf(for {
        key <- traceStateKeyArb.arbitrary
        value <- traceStateValueArb.arbitrary
      } yield key -> value)
      .suchThat(_.size < 32)
      .map { kvs =>
        TraceState(kvs.toMap).get
      }
  )

  implicit val batchArb: Arbitrary[Batch] = Arbitrary(for {
    size <- Gen.choose(1, 1)
    process <- Arbitrary.arbitrary[TraceProcess]
    spans <- Gen.listOfN(size, Arbitrary.arbitrary[CompletedSpan])
  } yield Batch(process, spans))
}

object ArbitraryInstances extends ArbitraryInstances
