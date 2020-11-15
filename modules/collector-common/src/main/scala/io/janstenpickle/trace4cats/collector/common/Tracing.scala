package io.janstenpickle.trace4cats.collector.common

import java.net.InetAddress

import cats.Applicative
import cats.data.NonEmptyList
import cats.effect.{Concurrent, Resource, Sync, Timer}
import cats.syntax.functor._
import cats.syntax.traverse._
import fs2.{Chunk, Pipe}
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.collector.common.config.{KafkaListenerConfig, ListenerConfig, TracingConfig}
import io.janstenpickle.trace4cats.kernel.{SpanExporter, SpanSampler}
import io.janstenpickle.trace4cats.meta.{PipeTracer, TracedSpanExporter}
import io.janstenpickle.trace4cats.model.{AttributeValue, CompletedSpan, TraceProcess}
import io.janstenpickle.trace4cats.rate.sampling.RateSpanSampler

object Tracing {
  def process[F[_]: Sync]: F[TraceProcess] = Sync[F].delay(InetAddress.getLocalHost.getHostName).map { hostname =>
    TraceProcess("trace4cats-agent", Map("hostname" -> hostname))
  }

  def sampler[F[_]: Concurrent: Timer](config: Option[TracingConfig], bufferSize: Int): F[Option[SpanSampler[F]]] =
    config.traverse(
      _.sampleRate.fold(Applicative[F].pure(SpanSampler.always[F]))(rate => RateSpanSampler.create[F](bufferSize, rate))
    )

  def pipe[F[_]: Concurrent: Timer](
    sampler: Option[SpanSampler[F]],
    process: TraceProcess,
    listener: ListenerConfig,
    kafka: Option[KafkaListenerConfig],
    bufferSize: Int
  ): F[Pipe[F, CompletedSpan, CompletedSpan]] =
    sampler match {
      case None => Applicative[F].pure[Pipe[F, CompletedSpan, CompletedSpan]](identity)
      case Some(sampler) =>
        PipeTracer[F](
          List[(String, AttributeValue)](
            "listen.protocols" -> AttributeValue.StringList(
              NonEmptyList("tcp", List("udp") ++ kafka.map(_ => "kafka"))
            ),
            "listen.format" -> "avro",
            "listen.port" -> listener.port
          ) ++ kafka.toList.flatMap { kafkaConfig =>
            List(
              "bootstrap.servers" -> AttributeValue.StringList(kafkaConfig.bootstrapServers),
              "topic" -> kafkaConfig.topic,
              "consumer.group" -> kafkaConfig.group
            )
          },
          process,
          sampler,
          bufferSize
        )
    }

  def exporter[F[_]: Concurrent: Timer: Logger](
    sampler: Option[SpanSampler[F]],
    name: String,
    attributes: List[(String, AttributeValue)],
    process: TraceProcess,
    bufferSize: Int,
    exporter: SpanExporter[F, Chunk],
  ): Resource[F, (String, SpanExporter[F, Chunk])] =
    sampler match {
      case None => Resource.pure[F, (String, SpanExporter[F, Chunk])](name -> exporter)
      case Some(sampler) =>
        TracedSpanExporter[F](name, attributes, process, sampler, exporter, bufferSize = bufferSize).map(name -> _)
    }
}
