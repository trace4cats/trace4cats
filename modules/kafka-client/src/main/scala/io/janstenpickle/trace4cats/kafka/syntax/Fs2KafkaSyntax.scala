package io.janstenpickle.trace4cats.kafka.syntax

import cats.effect.Bracket
import cats.{ApplicativeError, Defer, Functor, Monad}
import fs2.Stream
import fs2.kafka.{CommittableConsumerRecord, KafkaProducer}
import io.janstenpickle.trace4cats.ToHeaders
import io.janstenpickle.trace4cats.fs2.TracedStream
import io.janstenpickle.trace4cats.inject.{EntryPoint, LiftTrace, Provide, Trace}
import io.janstenpickle.trace4cats.kafka.{TracedConsumer, TracedProducer}

trait Fs2KafkaSyntax {
  implicit class ProducerSyntax[F[_], G[_], K, V](producer: KafkaProducer[F, K, V]) {
    def liftTrace(
      toHeaders: ToHeaders = ToHeaders.all
    )(implicit G: Monad[G], trace: Trace[G], lift: LiftTrace[F, G]): KafkaProducer[G, K, V] =
      TracedProducer.create[F, G, K, V](producer, toHeaders)
  }

  implicit class ConsumerSyntax[F[_], G[_], K, V](consumerStream: Stream[F, CommittableConsumerRecord[F, K, V]]) {
    def inject(ep: EntryPoint[F])(implicit
      F: Bracket[F, Throwable],
      G: Functor[G],
      trace: Trace[G],
      provide: Provide[F, G]
    ): TracedStream[F, CommittableConsumerRecord[F, K, V]] =
      TracedConsumer.inject[F, G, K, V](consumerStream)(ep)

    def injectK(ep: EntryPoint[F])(implicit
      F: Bracket[F, Throwable],
      deferF: Defer[F],
      G: ApplicativeError[G, Throwable],
      deferG: Defer[G],
      trace: Trace[G],
      provide: Provide[F, G],
      liftTrace: LiftTrace[F, G]
    ): TracedStream[G, CommittableConsumerRecord[G, K, V]] =
      TracedConsumer.injectK[F, G, K, V](consumerStream)(ep)
  }
}
