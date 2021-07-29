package io.janstenpickle.trace4cats.test

import java.time.Instant
import cats.Eval
import cats.data.NonEmptyList
import fs2.Chunk
import io.janstenpickle.trace4cats.model._
import org.scalacheck.{Arbitrary, Gen}

trait ArbitraryInstances extends ArbitraryAttributeValues {
  private def byteArray(length: Int) = Gen.listOfN(length, Arbitrary.arbByte.arbitrary).map(_.toArray)

  implicit val doubleArb: Arbitrary[Double] = Arbitrary(Gen.chooseNum(-1000.0, 1000.0).map(_ + 0.5))

  implicit val instantArb: Arbitrary[Instant] = Arbitrary(Gen.choose(0L, 1593882556588L).map(Instant.ofEpochMilli))

  implicit val stringArb: Arbitrary[String] = Arbitrary(for {
    size <- Gen.choose(1, 5)
    chars <- Gen.listOfN(size, Gen.alphaNumChar)
  } yield new String(chars.toArray))

  implicit val sampleArb: Arbitrary[SampleDecision] = Arbitrary(Gen.oneOf(SampleDecision.Include, SampleDecision.Drop))

  implicit val spanIdArb: Arbitrary[SpanId] = Arbitrary(byteArray(8).map(SpanId(_).get))
  implicit val traceIdArb: Arbitrary[TraceId] = Arbitrary(byteArray(16).map(TraceId(_).get))

  implicit val traceStateKeyArb: Arbitrary[TraceState.Key] = Arbitrary(
    stringArb.arbitrary.suchThat(_.nonEmpty).map(k => TraceState.Key(k.toLowerCase).get)
  )
  implicit val traceStateValueArb: Arbitrary[TraceState.Value] = Arbitrary(
    Gen
      .stringOf(Gen.alphaNumChar)
      .suchThat(s => s.nonEmpty && s.length < 256)
      .map(value => TraceState.Value.unsafe(value))
  )
  implicit val traceStateArb: Arbitrary[TraceState] = Arbitrary(
    Gen
      .mapOf(Gen.zip(traceStateKeyArb.arbitrary, traceStateValueArb.arbitrary))
      .suchThat(_.size < 32)
      .map(kvs => TraceState(kvs).get)
  )

  implicit val traceHeadersArb: Arbitrary[TraceHeaders] =
    Arbitrary(Gen.mapOf(Gen.zip(stringArb.arbitrary, stringArb.arbitrary)).map(values => TraceHeaders.of(values)))

  implicit def evalArb[A: Arbitrary]: Arbitrary[Eval[A]] = Arbitrary(Arbitrary.arbitrary[A].map(Eval.later(_)))

  implicit val spanKindArb: Arbitrary[SpanKind] =
    Arbitrary(Gen.oneOf(SpanKind.Server, SpanKind.Client, SpanKind.Producer, SpanKind.Consumer, SpanKind.Internal))
  implicit val spanStatusArb: Arbitrary[SpanStatus] = Arbitrary(
    Gen.oneOf(
      Gen.const(SpanStatus.Ok),
      Gen.const(SpanStatus.Cancelled),
      Gen.const(SpanStatus.Unknown),
      Gen.const(SpanStatus.InvalidArgument),
      Gen.const(SpanStatus.DeadlineExceeded),
      Gen.const(SpanStatus.NotFound),
      Gen.const(SpanStatus.AlreadyExists),
      Gen.const(SpanStatus.PermissionDenied),
      Gen.const(SpanStatus.ResourceExhausted),
      Gen.const(SpanStatus.FailedPrecondition),
      Gen.const(SpanStatus.Aborted),
      Gen.const(SpanStatus.OutOfRange),
      Gen.const(SpanStatus.Unimplemented),
      Gen.const(SpanStatus.Unavailable),
      Gen.const(SpanStatus.DataLoss),
      Gen.const(SpanStatus.Unauthenticated),
      stringArb.arbitrary.map(SpanStatus.Internal(_))
    )
  )

  implicit val linkArb: Arbitrary[Link] = Arbitrary(for {
    traceId <- traceIdArb.arbitrary
    spanId <- spanIdArb.arbitrary
  } yield Link(traceId, spanId))

  implicit val metaTraceArb: Arbitrary[MetaTrace] = Arbitrary(for {
    traceId <- traceIdArb.arbitrary
    spanId <- spanIdArb.arbitrary
  } yield MetaTrace(traceId, spanId))

  implicit val parentArb: Arbitrary[Parent] = Arbitrary(for {
    spanId <- spanIdArb.arbitrary
    isRemote <- Arbitrary.arbBool.arbitrary
  } yield Parent(spanId, isRemote))

  implicit val spanContextArb: Arbitrary[SpanContext] = Arbitrary(
    Gen.sized(size =>
      for {
        traceId <- traceIdArb.arbitrary
        spanId <- spanIdArb.arbitrary
        parent <- Gen.option(parentArb.arbitrary)
        traceFlags <- Arbitrary.arbBool.arbitrary.map(b => TraceFlags(SampleDecision.fromBoolean(b)))
        traceState <- Gen.resize(size / 2, traceStateArb.arbitrary)
        isRemote <- Arbitrary.arbBool.arbitrary
      } yield SpanContext(traceId, spanId, parent, traceFlags, traceState, isRemote)
    )
  )

  implicit val completedSpanBuilderArb: Arbitrary[CompletedSpan.Builder] = Arbitrary(
    Gen.sized(size =>
      for {
        context <- Gen.resize(size / 5, spanContextArb.arbitrary)
        name <- stringArb.arbitrary
        kind <- spanKindArb.arbitrary
        start <- instantArb.arbitrary
        end <- instantArb.arbitrary.suchThat(_.isAfter(start))
        attributes <- Gen.resize(size / 3, Gen.mapOf(Gen.zip(stringArb.arbitrary, attributeValueArb.arbitrary)))
        status <- spanStatusArb.arbitrary
        links <- Gen.option(
          Gen.resize(size / 10, Gen.nonEmptyListOf(linkArb.arbitrary).map(NonEmptyList.fromListUnsafe))
        )
        metaTrace <- Gen.option(metaTraceArb.arbitrary)
      } yield CompletedSpan.Builder(context, name, kind, start, end, attributes, status, links, metaTrace)
    )
  )

  implicit val traceProcessArb: Arbitrary[TraceProcess] = Arbitrary(
    Gen.sized(size =>
      for {
        name <- stringArb.arbitrary
        attributes <- Gen.resize(size / 4, Gen.mapOf(Gen.zip(stringArb.arbitrary, attributeValueArb.arbitrary)))
      } yield TraceProcess(name, attributes)
    )
  )

  implicit val completedSpanArb: Arbitrary[CompletedSpan] =
    Arbitrary(completedSpanBuilderArb.arbitrary.flatMap(b => traceProcessArb.arbitrary.map(b.build)))

  implicit val batchArb: Arbitrary[Batch[Chunk]] = Arbitrary(for {
    size <- Gen.choose(1, 3)
    spans <- Gen.listOfN(size, Arbitrary.arbitrary[CompletedSpan])
  } yield Batch(Chunk.seq(spans)))
}

object ArbitraryInstances extends ArbitraryInstances
