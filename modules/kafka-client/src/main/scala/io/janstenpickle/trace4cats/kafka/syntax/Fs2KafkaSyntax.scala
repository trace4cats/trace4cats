package io.janstenpickle.trace4cats.kafka.syntax

import cats.effect.{ApplicativeThrow, BracketThrow, MonadThrow}
import cats.{Defer, Functor, Monad}
import fs2.Stream
import fs2.kafka.{CommittableConsumerRecord, KafkaProducer}
import io.janstenpickle.trace4cats.base.context.{Init, Inject, Lift, Provide}
import io.janstenpickle.trace4cats.fs2.TracedStream
import io.janstenpickle.trace4cats.inject.{EntryPoint, Trace}
import io.janstenpickle.trace4cats.kafka.{TracedConsumer, TracedProducer}
import io.janstenpickle.trace4cats.model.{SpanKind, TraceHeaders}
import io.janstenpickle.trace4cats.{Span, ToHeaders}

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
      G: Monad[G],
      T: Trace[G],
    ): TracedStream[F, CommittableConsumerRecord[F, K, V]] = {
      implicit val inject: Inject[F, Span[F], (String, SpanKind, TraceHeaders)] = EntryPoint.injectContinueKind[F](ep)

      TracedConsumer.inject[F, G, K, V](consumerStream)
    }

    def trace[G[_]](implicit
      I: Init[F, G, Span[F], (String, SpanKind, TraceHeaders)],
      F: BracketThrow[F],
      G: Functor[G],
      T: Trace[G]
    ): TracedStream[F, CommittableConsumerRecord[F, K, V]] =
      TracedConsumer.inject[F, G, K, V](consumerStream)

    def injectK[G[_]](ep: EntryPoint[F])(implicit
      P: Provide[F, G, Span[F]],
      F: BracketThrow[F],
      deferF: Defer[F],
      G: MonadThrow[G],
      deferG: Defer[G],
      trace: Trace[G]
    ): TracedStream[G, CommittableConsumerRecord[G, K, V]] = {
      implicit val inject: Inject[F, Span[F], (String, SpanKind, TraceHeaders)] = EntryPoint.injectContinueKind[F](ep)

      TracedConsumer.injectK[F, G, K, V](consumerStream)
    }

    def traceK[G[_]](implicit
      I: Init[F, G, Span[F], (String, SpanKind, TraceHeaders)],
      F: BracketThrow[F],
      deferF: Defer[F],
      G: ApplicativeThrow[G],
      deferG: Defer[G],
      T: Trace[G]
    ): TracedStream[G, CommittableConsumerRecord[G, K, V]] =
      TracedConsumer.injectK[F, G, K, V](consumerStream)
  }
}
