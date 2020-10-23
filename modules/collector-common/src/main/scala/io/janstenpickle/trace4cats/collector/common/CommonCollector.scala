package io.janstenpickle.trace4cats.collector.common

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Resource, Timer}
import cats.implicits._
import cats.{Applicative, Parallel}
import com.monovore.decline._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import io.janstenpickle.trace4cats.`export`.QueuedSpanExporter
import io.janstenpickle.trace4cats.avro._
import io.janstenpickle.trace4cats.avro.kafka.{AvroKafkaConsumer, AvroKafkaSpanExporter}
import io.janstenpickle.trace4cats.avro.server.AvroServer
import io.janstenpickle.trace4cats.collector.common.config.{CommonCollectorConfig, ConfigParser, StackdriverHttpConfig}
import io.janstenpickle.trace4cats.datadog.DataDogSpanExporter
import io.janstenpickle.trace4cats.jaeger.JaegerSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.log.LogSpanExporter
import io.janstenpickle.trace4cats.newrelic.NewRelicSpanExporter
import io.janstenpickle.trace4cats.opentelemetry.otlp.OpenTelemetryOtlpHttpSpanExporter
import io.janstenpickle.trace4cats.sampling.tail.{SampleDecisionStore, TailSamplingSpanExporter, TailSpanSampler}
import io.janstenpickle.trace4cats.strackdriver.StackdriverHttpSpanExporter

object CommonCollector {
  val configFileOpt: Opts[String] =
    Opts.option[String]("config-file", "Configuration file location, may be in YAML or JSON format")

  def apply[F[_]: ConcurrentEffect: Parallel: ContextShift: Timer: Logger](
    blocker: Blocker,
    configFile: String,
    others: List[(String, SpanExporter[F])]
  ): Resource[F, Stream[F, Unit]] =
    for {
      config <- Resource.liftF(ConfigParser.parse[F, CommonCollectorConfig](configFile))
      _ <- Resource.make(
        Logger[F].info(
          s"Starting Trace 4 Cats Collector listening on tcp://::${config.listener.port} and udp://::${config.listener.port}"
        )
      )(_ => Logger[F].info("Shutting down Trace 4 Cats Collector"))

      client <- Resource.liftF(Http4sJdkClient[F](blocker))

      collectorExporter <- config.forwarder.traverse { forwarder =>
        AvroSpanExporter.tcp[F](blocker, host = forwarder.host, port = forwarder.port).map("Trace4Cats Avro TCP" -> _)
      }

      jaegerUdpExporter <- config.jaeger.traverse { jaeger =>
        JaegerSpanExporter[F](blocker, host = jaeger.host, port = jaeger.port).map("Jaeger UDP" -> _)
      }

      logExporter <- if (config.logSpans)
        Resource.pure[F, SpanExporter[F]](LogSpanExporter[F]).map(e => Some("Log" -> e))
      else Resource.pure[F, Option[(String, SpanExporter[F])]](None)

      otHttpExporter <- config.otlpHttp.traverse { otlp =>
        Resource.liftF(
          OpenTelemetryOtlpHttpSpanExporter[F](client, host = otlp.host, port = otlp.port)
            .map("OpenTelemetry HTTP" -> _)
        )
      }

      stackdriverExporter <- Resource.liftF(config.stackdriverHttp.traverse {
        case StackdriverHttpConfig(Some(projectId), Some(credsFile), _) =>
          StackdriverHttpSpanExporter[F](projectId, credsFile, client).map("Stackdriver HTTP" -> _)
        case StackdriverHttpConfig(_, _, None) =>
          StackdriverHttpSpanExporter[F](client).map("Stackdriver HTTP" -> _)
        case StackdriverHttpConfig(_, _, Some(serviceAccount)) =>
          StackdriverHttpSpanExporter[F](client, serviceAccount).map("Stackdriver HTTP" -> _)
      })

      ddExporter <- config.datadog.traverse { datadog =>
        Resource.liftF(
          DataDogSpanExporter[F](client, host = datadog.host, port = datadog.port).map("DataDog Agent" -> _)
        )
      }

      newRelicExporter <- config.newRelic.traverse { newRelic =>
        Resource.liftF(
          NewRelicSpanExporter[F](client, apiKey = newRelic.apiKey, endpoint = newRelic.endpoint)
            .map("NewRelic HTTP" -> _)
        )
      }

      kafkaExporter <- config.kafkaForwarder
        .traverse { kafka =>
          AvroKafkaSpanExporter[F](blocker, kafka.bootstrapServers, kafka.topic).map("Kafka" -> _)
        }

      queuedExporter <- QueuedSpanExporter(
        config.bufferSize,
        List(
          collectorExporter,
          jaegerUdpExporter,
          logExporter,
          otHttpExporter,
          stackdriverExporter,
          ddExporter,
          newRelicExporter,
          kafkaExporter
        ).flatten ++ others
      )

      exporter <- Resource.liftF(config.sampling.fold(Applicative[F].pure(queuedExporter)) { sampling =>
        SampleDecisionStore.localCache[F]().map(TailSpanSampler.probabilistic[F](sampling.sampleProbability, _)).map {
          sampler =>
            TailSamplingSpanExporter[F](queuedExporter, sampler)
        }
      })

      tcp <- AvroServer.tcp[F](blocker, exporter.pipe, config.listener.port)
      udp <- AvroServer.udp[F](blocker, exporter.pipe, config.listener.port)
      network = tcp.concurrently(udp)

      kafka = config.kafkaListener.map { kafka =>
        AvroKafkaConsumer[F](
          blocker,
          kafka.bootstrapServers,
          kafka.group,
          kafka.topic,
          exporter.pipe,
          batch = kafka.batch
        )
      }
    } yield kafka.fold(network)(network.concurrently(_))

}
