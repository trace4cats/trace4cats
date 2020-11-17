package io.janstenpickle.trace4cats.kafka

import cats.effect.Bracket
import cats.syntax.functor._
import cats.{ApplicativeError, Defer, Functor}
import fs2.Stream
import fs2.kafka.{CommittableConsumerRecord, CommittableOffset}
import io.janstenpickle.trace4cats.fs2.TracedStream
import io.janstenpickle.trace4cats.fs2.syntax.Fs2StreamSyntax
import io.janstenpickle.trace4cats.inject.{EntryPoint, LiftTrace, Provide, Trace}
import io.janstenpickle.trace4cats.model.{AttributeValue, SpanKind, TraceHeaders}

object TracedConsumer extends Fs2StreamSyntax {

  def inject[F[_]: Bracket[*[_], Throwable], G[_]: Functor: Trace, K, V](
    stream: Stream[F, CommittableConsumerRecord[F, K, V]]
  )(ep: EntryPoint[F])(implicit provide: Provide[F, G]): TracedStream[F, CommittableConsumerRecord[F, K, V]] =
    stream
      .injectContinue(ep, "kafka.receive", SpanKind.Consumer) { record =>
        TraceHeaders(record.record.headers.toChain.foldLeft(Map.empty[String, String]) { (acc, header) =>
          acc.updated(header.key(), header.as[String])
        })
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

  def injectK[F[_]: Bracket[*[_], Throwable]: Defer, G[_]: ApplicativeError[*[_], Throwable]: Defer: Trace, K, V](
    stream: Stream[F, CommittableConsumerRecord[F, K, V]]
  )(ep: EntryPoint[F])(implicit
    provide: Provide[F, G],
    liftTrace: LiftTrace[F, G]
  ): TracedStream[G, CommittableConsumerRecord[G, K, V]] = {
    def liftConsumerRecord(record: CommittableConsumerRecord[F, K, V]): CommittableConsumerRecord[G, K, V] =
      CommittableConsumerRecord[G, K, V](
        record.record,
        CommittableOffset(
          record.offset.topicPartition,
          record.offset.offsetAndMetadata,
          record.offset.consumerGroupId,
          _ => liftTrace(record.offset.commit)
        )
      )

    inject[F, G, K, V](stream)(ep).liftTrace[G].map(liftConsumerRecord)
  }

}
