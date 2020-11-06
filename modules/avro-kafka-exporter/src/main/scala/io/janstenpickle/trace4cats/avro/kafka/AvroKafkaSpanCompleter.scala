package io.janstenpickle.trace4cats.avro.kafka

import cats.data.NonEmptyList
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Resource, Timer}
import fs2.kafka.{KafkaProducer, ProducerSettings}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.QueuedSpanCompleter
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.{CompletedSpan, TraceId, TraceProcess}

import scala.concurrent.duration._

object AvroKafkaSpanCompleter {
  def apply[F[_]: ConcurrentEffect: ContextShift: Timer](
    blocker: Blocker,
    process: TraceProcess,
    bootStrapServers: NonEmptyList[String],
    topic: String,
    modifySettings: ProducerSettings[F, TraceId, CompletedSpan] => ProducerSettings[F, TraceId, CompletedSpan] =
      (x: ProducerSettings[F, TraceId, CompletedSpan]) => x,
    bufferSize: Int = 2000,
    batchSize: Int = 50,
    batchTimeout: FiniteDuration = 10.seconds
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter <- AvroKafkaSpanExporter[F](blocker, bootStrapServers, topic, modifySettings)
      completer <- QueuedSpanCompleter[F](process, exporter, bufferSize, batchSize, batchTimeout)
    } yield completer

  def fromProducer[F[_]: ConcurrentEffect: ContextShift: Timer](
    process: TraceProcess,
    producer: KafkaProducer[F, TraceId, CompletedSpan],
    topic: String,
    bufferSize: Int = 2000,
    batchSize: Int = 50,
    batchTimeout: FiniteDuration = 10.seconds
  ): Resource[F, SpanCompleter[F]] =
    for {
      implicit0(logger: Logger[F]) <- Resource.liftF(Slf4jLogger.create[F])
      exporter = AvroKafkaSpanExporter.fromProducer[F](producer, topic)
      completer <- QueuedSpanCompleter[F](process, exporter, bufferSize, batchSize, batchTimeout)
    } yield completer
}
