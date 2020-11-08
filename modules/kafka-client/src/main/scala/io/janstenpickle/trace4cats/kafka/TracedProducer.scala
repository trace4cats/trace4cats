package io.janstenpickle.trace4cats.kafka

import cats.data.NonEmptyList
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, Monad}
import fs2.kafka._
import io.janstenpickle.trace4cats.ToHeaders
import io.janstenpickle.trace4cats.inject.{LiftTrace, Trace}
import io.janstenpickle.trace4cats.model.{AttributeValue, SpanKind}

object TracedProducer {
  def create[F[_], G[_]: Monad: Trace, K, V](producer: KafkaProducer[F, K, V], toHeaders: ToHeaders = ToHeaders.all)(
    implicit lift: LiftTrace[F, G]
  ): KafkaProducer[G, K, V] =
    new KafkaProducer[G, K, V] {
      override def produce[P](records: ProducerRecords[K, V, P]): G[G[ProducerResult[K, V, P]]] =
        Trace[G].span("kafka.send", SpanKind.Producer) {
          Trace[G].headers(toHeaders).flatMap { traceHeaders =>
            val msgHeaders = Headers.fromIterable(traceHeaders.map { case (k, v) => Header(k, v) })

            NonEmptyList
              .fromList(records.records.map(_.topic).toList)
              .fold(Applicative[G].unit)(topics => Trace[G].put("topics", AttributeValue.StringList(topics))) >> lift(
              producer.produce(ProducerRecords(records.records.map(_.withHeaders(msgHeaders)), records.passthrough))
            ).map(lift.lift)
          }
        }
    }
}
