package io.janstenpickle.trace4cats.collector.common

import cats.data.Kleisli
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Resource, Timer}
import cats.implicits._
import cats.{Applicative, Parallel}
import com.monovore.decline._
import fs2.Stream
import io.chrisdavenport.log4cats.Logger
import io.jaegertracing.thrift.internal.senders.UdpSender
import io.janstenpickle.trace4cats.`export`.QueuedSpanExporter
import io.janstenpickle.trace4cats.avro._
import io.janstenpickle.trace4cats.avro.server.AvroServer
import io.janstenpickle.trace4cats.datadog.DataDogSpanExporter
import io.janstenpickle.trace4cats.jaeger.JaegerSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.log.LogSpanExporter
import io.janstenpickle.trace4cats.newrelic.{Endpoint, NewRelicSpanExporter}
import io.janstenpickle.trace4cats.opentelemetry.otlp.OpenTelemetryOtlpHttpSpanExporter
import io.janstenpickle.trace4cats.strackdriver.StackdriverHttpSpanExporter

object CommonCollector {
  val portOpt: Opts[Int] =
    Opts
      .env[Int](CollectorPortEnv, help = "The port to run on.")
      .orElse(Opts.option[Int]("port", "The port to run on"))
      .withDefault(DefaultPort)

  val collectorHostOpt: Opts[Option[String]] =
    Opts
      .env[String](CollectorHostEnv, "Collector hostname to forward spans")
      .orElse(Opts.option[String]("collector", "Collector hostname"))
      .orNone

  val collectorPortOpt: Opts[Int] =
    Opts
      .env[Int](CollectorPortEnv, "Collector port to forward spans")
      .orElse(Opts.option[Int]("collector-port", "Collector port"))
      .withDefault(DefaultPort)

  val jaegerUdpHostOpt: Opts[Option[String]] = Opts
    .option[String]("jaeger-udp-host", "Jaeger UDP agent host")
    .orNone
  val jaegerUdpPortOpt: Opts[Int] = Opts
    .option[Int]("jaeger-udp-port", "Jaeger UDP agent port")
    .withDefault(UdpSender.DEFAULT_AGENT_UDP_COMPACT_PORT)

  val logOpt: Opts[Boolean] = Opts.flag("log", "Write spans to the log").orFalse

  val otlpHttpHostOpt: Opts[Option[String]] =
    Opts.option[String]("opentelemetry-otlp-http-host", "Write spans via OpenTelemetry OLTP HTTP/JSON format").orNone
  val otlpHttpPortOpt: Opts[Int] =
    Opts.option[Int]("opentelemetry-otpl-http-port", "OpenTelelmetry OLTP HTTP port").withDefault(55681)

  val stackdriverHttpOpt: Opts[Boolean] = Opts
    .flag(
      "stackdriver-http",
      "Use stackdriver in HTTP mode. Requires a credentials file to be provided along with project ID"
    )
    .orTrue

  val stackdriverProjectOpt: Opts[Option[String]] =
    Opts.option[String]("stackdriver-project-id", "Google Project ID for use with Stackdriver").orNone

  val stackdriverCredentialsFileOpt: Opts[Option[String]] = Opts
    .option[String](
      "stackdriver-credentials-file",
      "Google service account credenitals file for publishing to stackdriver"
    )
    .orElse(
      Opts.env[String]("GOOGLE_APPLICATION_CREDENTIALS", "Google service account file location environment variable")
    )
    .orNone

  val dataDogHostOpt: Opts[Option[String]] =
    Opts.option[String]("datadog-agent-host", "Write spans to the DataDog agent at this host").orNone
  val dataDogPortOpt: Opts[Int] =
    Opts.option[Int]("datadog-agent-port", "DataDog agent port").withDefault(8126)

  val newRelicApiKeyOpt: Opts[Option[String]] =
    Opts.option[String]("newrelic-api-key", "NewRelic API Key for use with NewRelic exporter").orNone

  val newRelicEndpointOpt: Opts[Endpoint] = Opts
    .option[String](
      "newrelic-endpoint",
      "NewRelic endpoint where spans may be forwarded. Use US or EU for default endpoints for specify a custom observer URL"
    )
    .map {
      case "US" => Endpoint.US
      case "EU" => Endpoint.EU
      case endpoint => Endpoint.Observer(endpoint)
    }
    .withDefault(Endpoint.US)

  val bufferSizeOpt: Opts[Int] =
    Opts
      .option[Int]("buffer-size", "Number of batches to buffer in case of network issues")
      .withDefault(500)

  def apply[F[_]: ConcurrentEffect: Parallel: ContextShift: Timer]: Opts[
    Kleisli[Resource[F, *], (Blocker, Logger[F], List[(String, SpanExporter[F])]), Stream[F, Unit]]
  ] =
    (
      portOpt,
      collectorHostOpt,
      collectorPortOpt,
      jaegerUdpHostOpt,
      jaegerUdpPortOpt,
      logOpt,
      otlpHttpHostOpt,
      otlpHttpPortOpt,
      stackdriverHttpOpt,
      stackdriverProjectOpt,
      stackdriverCredentialsFileOpt,
      dataDogHostOpt,
      dataDogPortOpt,
      newRelicApiKeyOpt,
      newRelicEndpointOpt,
      bufferSizeOpt
    ).mapN(run[F])

  private def run[F[_]: ConcurrentEffect: Parallel: ContextShift: Timer](
    port: Int,
    collectorHost: Option[String],
    collectorPort: Int,
    jaegerUdpHost: Option[String],
    jaegerUdpPort: Int,
    log: Boolean,
    otHttpHost: Option[String],
    otHttpPort: Int,
    stackdriverHttp: Boolean,
    stackdriverProject: Option[String],
    stackdriverCredentialsFile: Option[String],
    dataDogHost: Option[String],
    dataDogPort: Int,
    newRelicApiKey: Option[String],
    newRelicEndpoint: Endpoint,
    bufferSize: Int
  ): Kleisli[Resource[F, *], (Blocker, Logger[F], List[(String, SpanExporter[F])]), Stream[F, Unit]] = Kleisli {
    case (blocker, implicit0(logger: Logger[F]), others) =>
      for {
        _ <- Resource.make(
          logger.info(s"Starting Trace 4 Cats Collector listening on tcp://::$port and udp://::$port")
        )(_ => logger.info("Shutting down Trace 4 Cats Collector"))

        collectorExporter <- collectorHost.traverse { host =>
          AvroSpanExporter.tcp[F](blocker, host = host, port = collectorPort).map("Trace4Cats Avro TCP" -> _)
        }

        jaegerUdpExporter <- jaegerUdpHost.traverse { host =>
          JaegerSpanExporter[F](blocker, host = host, port = jaegerUdpPort).map("Jaeger UDP" -> _)
        }

        logExporter <- if (log) Resource.pure[F, SpanExporter[F]](LogSpanExporter[F]).map(e => Some("Log" -> e))
        else Resource.pure[F, Option[(String, SpanExporter[F])]](None)

        otHttpExporter <- otHttpHost.traverse { host =>
          Resource.liftF(for {
            client <- Http4sJdkClient[F](blocker)
            exporter <- OpenTelemetryOtlpHttpSpanExporter[F](client, host = host, port = otHttpPort)
          } yield "OpenTelemetry HTTP" -> exporter)
        }

        stackdriverExporter <- (stackdriverProject, stackdriverCredentialsFile).mapN(_ -> _).flatTraverse {
          case (projectId, credsFile) =>
            Resource.liftF[F, Option[(String, SpanExporter[F])]] {
              if (stackdriverHttp)
                for {
                  client <- Http4sJdkClient[F](blocker)
                  exporter <- StackdriverHttpSpanExporter[F](projectId, credsFile, client)
                } yield Some("Stackdriver HTTP" -> exporter)
              else Applicative[F].pure(None)
            }
        }

        ddExporter <- dataDogHost.traverse { host =>
          Resource.liftF(for {
            client <- Http4sJdkClient[F](blocker)
            exporter <- DataDogSpanExporter[F](client, host = host, port = dataDogPort)
          } yield "DataDog Agent" -> exporter)
        }

        newRelicExporter <- newRelicApiKey.traverse { apiKey =>
          Resource.liftF(for {
            client <- Http4sJdkClient[F](blocker)
            exporter <- NewRelicSpanExporter[F](client, apiKey = apiKey, endpoint = newRelicEndpoint)
          } yield "NewRelic HTTP" -> exporter)
        }

        queuedExporter <- QueuedSpanExporter(
          bufferSize,
          List(
            collectorExporter,
            jaegerUdpExporter,
            logExporter,
            otHttpExporter,
            stackdriverExporter,
            ddExporter,
            newRelicExporter
          ).flatten ++ others
        )

        tcp <- AvroServer.tcp[F](blocker, _.evalMap(queuedExporter.exportBatch), port)
        udp <- AvroServer.udp[F](blocker, _.evalMap(queuedExporter.exportBatch), port)
      } yield tcp.concurrently(udp)
  }
}
