package io.janstenpickle.trace4cats.kafka

import cats.effect.{ApplicativeThrow, BracketThrow}
import cats.syntax.functor._
import cats.{Defer, Functor}
import fs2.Stream
import fs2.kafka.{CommittableConsumerRecord, CommittableOffset}
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.base.context.Provide
import io.janstenpickle.trace4cats.fs2.TracedStream
import io.janstenpickle.trace4cats.fs2.syntax.Fs2StreamSyntax
import io.janstenpickle.trace4cats.inject.{ResourceKleisli, SpanParams, Trace}
import io.janstenpickle.trace4cats.model.{AttributeValue, SpanKind}

object TracedConsumer extends Fs2StreamSyntax {

  def inject[F[_]: BracketThrow, G[_]: Functor: Trace, K, V](stream: Stream[F, CommittableConsumerRecord[F, K, V]])(
    k: ResourceKleisli[F, SpanParams, Span[F]]
  )(implicit P: Provide[F, G, Span[F]]): TracedStream[F, CommittableConsumerRecord[F, K, V]] =
    stream
      .traceContinue(k, "kafka.receive", SpanKind.Consumer) { record =>
        KafkaHeaders.converter.from(record.record.headers)
      }
      .evalMapTrace { record =>
        Trace[G]
          .putAll(
            "topic" -> record.record.topic,
            "consumer.group" -> AttributeValue.StringValue(record.offset.consumerGroupId.getOrElse("")),
            "create.time" -> AttributeValue.LongValue(record.record.timestamp.createTime.getOrElse(0L)),
            "log.append.time" -> AttributeValue.LongValue(record.record.timestamp.logAppendTime.getOrElse(0L)),
          )
          .as(record)
      }

  def injectK[F[_]: BracketThrow: Defer, G[_]: ApplicativeThrow: Defer: Trace, K, V](
    stream: Stream[F, CommittableConsumerRecord[F, K, V]]
  )(
    k: ResourceKleisli[F, SpanParams, Span[F]]
  )(implicit P: Provide[F, G, Span[F]]): TracedStream[G, CommittableConsumerRecord[G, K, V]] = {
    def liftConsumerRecord(record: CommittableConsumerRecord[F, K, V]): CommittableConsumerRecord[G, K, V] =
      CommittableConsumerRecord[G, K, V](
        record.record,
        CommittableOffset(
          record.offset.topicPartition,
          record.offset.offsetAndMetadata,
          record.offset.consumerGroupId,
          _ => P.lift(record.offset.commit)
        )
      )

    inject[F, G, K, V](stream)(k).liftTrace[G].map(liftConsumerRecord)
  }

}
