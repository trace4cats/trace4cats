package io.janstenpickle.trace4cats.kafka.syntax

import cats.{Functor, Monad}
import cats.effect.Bracket
import fs2.Stream
import fs2.kafka.{CommittableConsumerRecord, KafkaProducer}
import io.janstenpickle.trace4cats.ToHeaders
import io.janstenpickle.trace4cats.fs2.{Fs2EntryPoint, TracedStream}
import io.janstenpickle.trace4cats.inject.{LiftTrace, Provide, Trace}
import io.janstenpickle.trace4cats.kafka.{TracedConsumer, TracedProducer}

trait Fs2KafkaSyntax {
  implicit class ProducerSyntax[F[_], G[_], K, V](producer: KafkaProducer[F, K, V]) {
    def liftTrace(
      toHeaders: ToHeaders = ToHeaders.all
    )(implicit G: Monad[G], trace: Trace[G], lift: LiftTrace[F, G]): KafkaProducer[G, K, V] =
      TracedProducer.create[F, G, K, V](producer, toHeaders)
  }

  implicit class ConsumerSyntax[F[_], G[_], K, V](consumerStream: Stream[F, CommittableConsumerRecord[F, K, V]]) {
    def inject(ep: Fs2EntryPoint[F])(
      implicit F: Bracket[F, Throwable],
      G: Functor[G],
      trace: Trace[G],
      provide: Provide[F, G]
    ): TracedStream[F, CommittableConsumerRecord[F, K, V]] =
      TracedConsumer.inject[F, G, K, V](consumerStream)(ep)
  }
}
