package io.janstenpickle.trace4cats.kafka.syntax

import cats.effect.Bracket
import cats.{ApplicativeError, Defer, Functor, Monad}
import fs2.Stream
import fs2.kafka.{CommittableConsumerRecord, KafkaProducer}
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import io.janstenpickle.trace4cats.base.context.{Lift, Provide}
import io.janstenpickle.trace4cats.fs2.TracedStream
import io.janstenpickle.trace4cats.inject.{EntryPoint, Trace}
import io.janstenpickle.trace4cats.kafka.{TracedConsumer, TracedProducer}

trait Fs2KafkaSyntax {
  implicit class ProducerSyntax[F[_], K, V](producer: KafkaProducer[F, K, V]) {
    def liftTrace[G[_]](
      toHeaders: ToHeaders = ToHeaders.all
    )(implicit L: Lift[F, G], G: Monad[G], T: Trace[G]): KafkaProducer[G, K, V] =
      TracedProducer.create[F, G, K, V](producer, toHeaders)
  }

  implicit class ConsumerSyntax[F[_], K, V](consumerStream: Stream[F, CommittableConsumerRecord[F, K, V]]) {
    def inject[G[_]](ep: EntryPoint[F])(implicit
      P: Provide[F, G, Span[F]],
      F: Bracket[F, Throwable],
      G: Functor[G],
      T: Trace[G],
    ): TracedStream[F, CommittableConsumerRecord[F, K, V]] =
      TracedConsumer.inject[F, G, K, V](consumerStream)(ep)

    def injectK[G[_]](ep: EntryPoint[F])(implicit
      P: Provide[F, G, Span[F]],
      F: Bracket[F, Throwable],
      deferF: Defer[F],
      G: ApplicativeError[G, Throwable],
      deferG: Defer[G],
      trace: Trace[G]
    ): TracedStream[G, CommittableConsumerRecord[G, K, V]] =
      TracedConsumer.injectK[F, G, K, V](consumerStream)(ep)
  }
}
