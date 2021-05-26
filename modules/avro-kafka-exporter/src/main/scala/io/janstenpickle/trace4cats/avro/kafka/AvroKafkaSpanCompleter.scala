package io.janstenpickle.trace4cats.avro.kafka

import cats.data.NonEmptyList
import cats.effect.kernel.{Async, Resource}
import fs2.Chunk
import fs2.kafka.{KafkaProducer, ProducerSettings}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.`export`.{CompleterConfig, QueuedSpanCompleter}
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.model.{CompletedSpan, TraceId, TraceProcess}

object AvroKafkaSpanCompleter {
  def apply[F[_]: Async](
    process: TraceProcess,
    bootStrapServers: NonEmptyList[String],
    topic: String,
    modifySettings: ProducerSettings[F, TraceId, CompletedSpan] => ProducerSettings[F, TraceId, CompletedSpan] =
      (x: ProducerSettings[F, TraceId, CompletedSpan]) => x,
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] =
    Resource.eval(Slf4jLogger.create[F]).flatMap { implicit logger: Logger[F] =>
      AvroKafkaSpanExporter[F, Chunk](bootStrapServers, topic, modifySettings)
        .flatMap(QueuedSpanCompleter[F](process, _, config))
    }

  def fromProducer[F[_]: Async](
    process: TraceProcess,
    producer: KafkaProducer[F, TraceId, CompletedSpan],
    topic: String,
    config: CompleterConfig = CompleterConfig(),
  ): Resource[F, SpanCompleter[F]] = {
    val exporter = AvroKafkaSpanExporter.fromProducer[F, Chunk](producer, topic)
    Resource.eval(Slf4jLogger.create[F]).flatMap { implicit logger: Logger[F] =>
      QueuedSpanCompleter[F](process, exporter, config)
    }
  }
}
