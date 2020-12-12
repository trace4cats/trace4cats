package io.janstenpickle.trace4cats.kafka.syntax

import cats.effect.{ApplicativeThrow, BracketThrow}
import cats.{Defer, Functor, Monad}
import fs2.Stream
import fs2.kafka.{CommittableConsumerRecord, KafkaProducer}
import io.janstenpickle.trace4cats.{Span, ToHeaders}
import io.janstenpickle.trace4cats.base.context.{Lift, Provide}
import io.janstenpickle.trace4cats.fs2.TracedStream
import io.janstenpickle.trace4cats.inject.{EntryPoint, ResourceKleisli, SpanParams, Trace}
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
      F: BracketThrow[F],
      G: Functor[G],
      T: Trace[G],
    ): TracedStream[F, CommittableConsumerRecord[F, K, V]] =
      TracedConsumer.inject[F, G, K, V](consumerStream)(ep.toReader)

    def trace[G[_]](k: ResourceKleisli[F, SpanParams, Span[F]])(implicit
      P: Provide[F, G, Span[F]],
      F: BracketThrow[F],
      G: Functor[G],
      T: Trace[G],
    ): TracedStream[F, CommittableConsumerRecord[F, K, V]] =
      TracedConsumer.inject[F, G, K, V](consumerStream)(k)

    def injectK[G[_]](ep: EntryPoint[F])(implicit
      P: Provide[F, G, Span[F]],
      F: BracketThrow[F],
      deferF: Defer[F],
      G: ApplicativeThrow[G],
      deferG: Defer[G],
      trace: Trace[G]
    ): TracedStream[G, CommittableConsumerRecord[G, K, V]] =
      TracedConsumer.injectK[F, G, K, V](consumerStream)(ep.toReader)

    def traceK[G[_]](k: ResourceKleisli[F, SpanParams, Span[F]])(implicit
      P: Provide[F, G, Span[F]],
      F: BracketThrow[F],
      deferF: Defer[F],
      G: ApplicativeThrow[G],
      deferG: Defer[G],
      trace: Trace[G]
    ): TracedStream[G, CommittableConsumerRecord[G, K, V]] =
      TracedConsumer.injectK[F, G, K, V](consumerStream)(k)
  }
}
