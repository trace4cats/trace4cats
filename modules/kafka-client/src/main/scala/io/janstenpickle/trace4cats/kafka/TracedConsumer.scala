package io.janstenpickle.trace4cats.kafka

import cats.Functor
import cats.effect.Bracket
import cats.syntax.functor._
import fs2.Stream
import fs2.kafka.CommittableConsumerRecord
import io.janstenpickle.trace4cats.fs2.syntax.Fs2StreamSyntax
import io.janstenpickle.trace4cats.fs2.{Fs2EntryPoint, TracedStream}
import io.janstenpickle.trace4cats.inject.{Provide, Trace}
import io.janstenpickle.trace4cats.model.{AttributeValue, SpanKind}

object TracedConsumer extends Fs2StreamSyntax {

  def inject[F[_]: Bracket[*[_], Throwable], G[_]: Functor: Trace, K, V](
    stream: Stream[F, CommittableConsumerRecord[F, K, V]]
  )(ep: Fs2EntryPoint[F])(implicit provide: Provide[F, G]): TracedStream[F, CommittableConsumerRecord[F, K, V]] =
    stream
      .injectContinue(ep) { record =>
        record.record.headers.toChain.foldLeft(Map.empty[String, String]) { (acc, header) =>
          acc.updated(header.key(), header.as[String])
        }
      }
      .evalMapTrace("kafka.receive", SpanKind.Consumer) { record =>
        Trace[G]
          .putAll(
            "topic" -> record.record.topic,
            "consumer.group" -> AttributeValue.StringValue(record.offset.consumerGroupId.getOrElse("")),
            "create.time" -> AttributeValue.LongValue(record.record.timestamp.createTime.getOrElse(0L)),
            "log.append.time" -> AttributeValue.LongValue(record.record.timestamp.logAppendTime.getOrElse(0L)),
          )
          .as(record)
      }

}
