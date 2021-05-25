package io.janstenpickle.trace4cats.collector.common

import cats.Parallel
import cats.effect.kernel.{Async, Resource}
import cats.implicits._
import com.monovore.decline._
import fs2.kafka.ConsumerSettings
import fs2.{Chunk, Pipe, Stream}
import org.typelevel.log4cats.Logger
import io.janstenpickle.trace4cats.`export`.QueuedSpanExporter
import io.janstenpickle.trace4cats.avro._
import io.janstenpickle.trace4cats.avro.kafka.{AvroKafkaConsumer, AvroKafkaSpanExporter}
import io.janstenpickle.trace4cats.avro.server.AvroServer
import io.janstenpickle.trace4cats.collector.common.config.{
  BatchConfig,
  CommonCollectorConfig,
  ConfigParser,
  StackdriverHttpConfig
}
import io.janstenpickle.trace4cats.datadog.DataDogSpanExporter
import io.janstenpickle.trace4cats.jaeger.JaegerSpanExporter
import io.janstenpickle.trace4cats.kernel.{BuildInfo, SpanExporter}
import io.janstenpickle.trace4cats.log.LogSpanExporter
import io.janstenpickle.trace4cats.model.{AttributeValue, CompletedSpan, TraceId}
import io.janstenpickle.trace4cats.newrelic.NewRelicSpanExporter
import io.janstenpickle.trace4cats.opentelemetry.otlp.OpenTelemetryOtlpHttpSpanExporter
import io.janstenpickle.trace4cats.stackdriver.StackdriverHttpSpanExporter
import io.janstenpickle.trace4cats.zipkin.ZipkinHttpSpanExporter

import scala.concurrent.duration._

object CommonCollector {
  val configFileOpt: Opts[String] =
    Opts.option[String]("config-file", "Configuration file location, may be in YAML or JSON format")

  def apply[F[_]: Async: Parallel: Logger](
    configFile: String,
    others: List[(String, List[(String, AttributeValue)], SpanExporter[F, Chunk])]
  ): Resource[F, Stream[F, Unit]] =
    for {
      config <- Resource.eval(ConfigParser.parse[F, CommonCollectorConfig](configFile))
      _ <- Resource.make(
        Logger[F].info(
          s"Starting Trace 4 Cats Collector v${BuildInfo.version} listening on tcp://::${config.listener.port} and udp://::${config.listener.port}"
        )
      )(_ => Logger[F].info("Shutting down Trace 4 Cats Collector"))

      process <- Resource.eval(Tracing.process[F])
      traceSampler <- Tracing.sampler[F](config.tracing, config.bufferSize)

      client <- Http4sJdkClient[F]()

      collectorExporters <- config.forwarders.traverse { forwarder =>
        AvroSpanExporter
          .tcp[F, Chunk](host = forwarder.host, port = forwarder.port)
          .map(
            (
              "Trace4Cats Avro TCP",
              List[(String, AttributeValue)](
                "avro.host" -> forwarder.host,
                "avro.port" -> forwarder.port,
                "avro.protocol" -> "tcp"
              ),
              _
            )
          )
      }

      jaegerUdpExporters <- config.jaeger.traverse { jaeger =>
        JaegerSpanExporter[F, Chunk](process = None, host = jaeger.host, port = jaeger.port)
          .map(
            (
              "Jaeger UDP",
              List[(String, AttributeValue)]("jaeger.thrift.host" -> jaeger.host, "jaeger.thrift.port" -> jaeger.port),
              _
            )
          )
      }

      logExporter <-
        if (config.logSpans)
          Resource
            .pure[F, Option[(String, List[(String, AttributeValue)], SpanExporter[F, Chunk])]](
              Some(("Log", List.empty[(String, AttributeValue)], LogSpanExporter[F, Chunk]))
            )
        else Resource.pure[F, Option[(String, List[(String, AttributeValue)], SpanExporter[F, Chunk])]](None)

      otHttpExporters <- config.otlpHttp.traverse { otlp =>
        Resource
          .eval(OpenTelemetryOtlpHttpSpanExporter[F, Chunk](client, host = otlp.host, port = otlp.port))
          .map(
            (
              "OpenTelemetry HTTP",
              List[(String, AttributeValue)]("otlp.http.host" -> otlp.host, "otlp.http.port" -> otlp.port),
              _
            )
          )
      }

      stackdriverExporters <- config.stackdriverHttp.traverse {
        case StackdriverHttpConfig(Some(projectId), Some(credsFile), _) =>
          Resource
            .eval(StackdriverHttpSpanExporter[F, Chunk](projectId, credsFile, client))
            .map(("Stackdriver HTTP", List[(String, AttributeValue)]("stackdriver.http.project.id" -> projectId), _))
        case StackdriverHttpConfig(_, _, None) =>
          Resource
            .eval(StackdriverHttpSpanExporter[F, Chunk](client))
            .map(("Stackdriver HTTP", List.empty[(String, AttributeValue)], _))
        case StackdriverHttpConfig(_, _, Some(serviceAccount)) =>
          Resource
            .eval(StackdriverHttpSpanExporter[F, Chunk](client, serviceAccount))
            .map(
              (
                "Stackdriver HTTP",
                List[(String, AttributeValue)]("stackdriver.http.service.account" -> serviceAccount),
                _
              )
            )
      }

      ddExporters <- config.datadog.traverse { datadog =>
        Resource
          .eval(DataDogSpanExporter[F, Chunk](client, host = datadog.host, port = datadog.port))
          .map(
            (
              "DataDog Agent",
              List[(String, AttributeValue)](
                "datadog.agent.host" -> datadog.host,
                "datadog.agent.sport" -> datadog.port
              ),
              _
            )
          )

      }

      newRelicExporters <- config.newRelic.traverse { newRelic =>
        Resource
          .eval(NewRelicSpanExporter[F, Chunk](client, apiKey = newRelic.apiKey, endpoint = newRelic.endpoint))
          .map(("NewRelic HTTP", List[(String, AttributeValue)]("newrelic.endpoint" -> newRelic.endpoint.url), _))

      }

      zipkinExporters <- config.zipkin.traverse { zipkin =>
        Resource
          .eval(ZipkinHttpSpanExporter[F, Chunk](client, host = zipkin.host, port = zipkin.port))
          .map(
            (
              "Zipkin HTTP",
              List[(String, AttributeValue)]("zipkin.host" -> zipkin.host, "zipkin.port" -> zipkin.port),
              _
            )
          )

      }

      kafkaExporters <-
        config.kafkaForwarders
          .traverse { kafka =>
            AvroKafkaSpanExporter[F, Chunk](kafka.bootstrapServers, kafka.topic, _.withProperties(kafka.producerConfig))
              .map(
                (
                  "Kafka",
                  List[(String, AttributeValue)](
                    "kafka.bootstrap.servers" -> AttributeValue.StringList(kafka.bootstrapServers),
                    "kakfa.topic" -> kafka.topic
                  ),
                  _
                )
              )
          }

      allExporters = List(
        collectorExporters,
        jaegerUdpExporters,
        logExporter.toList,
        otHttpExporters,
        stackdriverExporters,
        ddExporters,
        newRelicExporters,
        zipkinExporters,
        kafkaExporters
      ).flatten ++ others

      exporterTraceAttrs = allExporters.flatMap(_._2)

      exporter <- QueuedSpanExporter(
        config.bufferSize,
        allExporters.map { case (name, _, exporter) => name -> exporter }
      ).map(
        Tracing.exporter[F](traceSampler, "Collector Combined", allExporters.map(_._1), exporterTraceAttrs, process, _)
      )

      samplingPipe <- Sampling.pipe[F](config.sampling)

      tracingPipe = Tracing.pipe[F](traceSampler, process, config.listener, config.kafkaListener)

      exportPipe: Pipe[F, CompletedSpan, Unit] = { stream: Stream[F, CompletedSpan] =>
        config.batch
          .fold(stream) { case BatchConfig(size, timeoutSeconds) =>
            stream.groupWithin(size, timeoutSeconds.seconds).flatMap(Stream.chunk)
          }
      }.andThen(samplingPipe)
        .andThen(AttributeFiltering.pipe[F](config.attributeFiltering))
        .andThen(tracingPipe)
        .andThen(exporter.pipe)

      tcp <- AvroServer.tcp[F](exportPipe, config.listener.port)
      udp <- AvroServer.udp[F](exportPipe, config.listener.port)
      network = tcp.concurrently(udp)

      kafka = config.kafkaListener.map { kafka =>
        AvroKafkaConsumer[F](
          kafka.bootstrapServers,
          kafka.group,
          kafka.topic,
          (s: ConsumerSettings[F, Option[TraceId], Option[CompletedSpan]]) => s.withProperties(kafka.consumerConfig)
        ).through(exportPipe)
      }
    } yield kafka.fold(network)(network.concurrently(_))

}
