package io.janstenpickle.trace4cats.avro

import cats.data.NonEmptyList
import cats.syntax.either._
import cats.{ApplicativeThrow, Eval}
import io.janstenpickle.trace4cats.model._
import org.apache.avro.Schema
import vulcan.generic._
import vulcan.{AvroError, Codec}

object AvroInstances {
  implicit val spanIdCodec: Codec[SpanId] =
    Codec.bytes.imapError(SpanId(_).toRight(AvroError("Invalid Span ID")))(_.value)

  implicit val traceIdCodec: Codec[TraceId] =
    Codec.bytes.imapError(TraceId(_).toRight(AvroError("Invalid Trace ID")))(_.value)

  implicit val traceStateKeyCodec: Codec[TraceState.Key] =
    Codec.string.imapError(TraceState.Key(_).toRight(AvroError("Invalid trace state key")))(_.k)

  implicit val traceStateValueCodec: Codec[TraceState.Value] =
    Codec.string.imapError(TraceState.Value(_).toRight(AvroError("Invalid trace state value")))(_.v)

  implicit val traceStateCodec: Codec[TraceState] = Codec
    .map[TraceState.Value]
    .imap(_.flatMap { case (k, v) => TraceState.Key(k).map(_ -> v) })(_.map { case (k, v) =>
      k.k -> v
    })
    .imapError[TraceState](TraceState(_).toRight(AvroError("Invalid trace state size")))(_.values)

  implicit val traceFlagsCodec: Codec[TraceFlags] =
    Codec.boolean.imap(sampled => TraceFlags(SampleDecision.fromBoolean(sampled)))(_.sampled.toBoolean)

  implicit val parentCodec: Codec[Parent] = Codec.derive

  implicit val spanContextCodec: Codec[SpanContext] = Codec.derive

  implicit def evalCodec[A: Codec]: Codec[Eval[A]] =
    Codec.instance(
      Codec[A].schema,
      a => Codec[A].encode(a.value),
      (obj, schema) => Codec[A].decode(obj, schema).map(Eval.later(_))
    )

  implicit val traceValueCodec: Codec[AttributeValue] = Codec.derive[AttributeValue]

  implicit val attributesCodec: Codec[Map[String, AttributeValue]] = Codec.map[AttributeValue]

  implicit val spanKindCodec: Codec[SpanKind] = Codec.derive[SpanKind]

  implicit val linkCodec: Codec[Link] = Codec.derive[Link]

  implicit val linksCodec: Codec[NonEmptyList[Link]] = Codec.nonEmptyList[Link]

  implicit val metaTraceCodec: Codec[MetaTrace] = Codec.derive[MetaTrace]

  implicit val spanStatusCodec: Codec[SpanStatus] = Codec.derive[SpanStatus]

  implicit val completedSpanCodec: Codec[CompletedSpan] = Codec.derive

  implicit val processCodec: Codec[TraceProcess] = Codec.derive

  def completedSpanSchema[F[_]: ApplicativeThrow]: F[Schema] =
    ApplicativeThrow[F].fromEither(completedSpanCodec.schema.leftMap(_.throwable))
}
