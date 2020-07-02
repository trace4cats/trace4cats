package io.janstenpickle.trace4cats.collector

import cats.effect.{Blocker, ExitCode, IO, Resource}
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.jaegertracing.thrift.internal.senders.UdpSender
import io.janstenpickle.trace4cats.avro._
import io.janstenpickle.trace4cats.avro.server.AvroServer
import io.janstenpickle.trace4cats.jaeger.JaegerSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.log.LogExporter
import io.janstenpickle.trace4cats.opentelemetry.OpenTelemetrySpanExporter
import io.janstenpickle.trace4cats.stackdriver.StackdriverGrpcSpanExporter
import io.janstenpickle.trace4cats.strackdriver.StackdriverHttpSpanExporter

object Collector
    extends CommandIOApp(name = "trace4cats-collector", header = "Trace 4 Cats Collector", version = "0.1.0") {

  val portOpt: Opts[Int] =
    Opts
      .env[Int](CollectorPortEnv, help = "The port to run on.")
      .orElse(Opts.option[Int]("port", "The port to run on"))
      .orNone
      .map(_.getOrElse(DefaultPort))

  val collectorHostOpt: Opts[Option[String]] =
    Opts
      .env[String](CollectorHostEnv, "Collector hostname to forward spans")
      .orElse(Opts.option[String]("collector", "Collector hostname"))
      .orNone

  val collectorPortOpt: Opts[Int] =
    Opts
      .env[Int](CollectorPortEnv, "Collector port to forward spans")
      .orElse(Opts.option[Int]("collector-port", "Collector port"))
      .orNone
      .map(_.getOrElse(DefaultPort))

  val jaegerUdpOpt: Opts[Boolean] = Opts.flag("jaeger-udp", "Send spans via Jaeger UDP").orFalse
  val jaegerUdpPortOpt: Opts[Int] = Opts
    .option[Int]("jaeger-udp-port", "Jaeger UDP agent port")
    .orNone
    .map(_.getOrElse(UdpSender.DEFAULT_AGENT_UDP_COMPACT_PORT))

  val jaegerUdpHostOpt: Opts[String] = Opts
    .option[String]("jaeger-udp-host", "Jaeger UDP agent host")
    .orNone
    .map(_.getOrElse(UdpSender.DEFAULT_AGENT_UDP_HOST))

  val logOpt: Opts[Boolean] = Opts.flag("log", "Write spans to the log").orFalse

  val otHostOpt: Opts[Option[String]] =
    Opts.option[String]("opentelemetry-host", "Write spans via OpenTelemetry protobufs format").orNone
  val otPortOpt: Opts[Int] =
    Opts.option[Int]("opentelemetry-host", "OpenTelelmetry protobufs port").orNone.map(_.getOrElse(55678))

  val stackdriverHttpOpt: Opts[Boolean] = Opts
    .flag(
      "stackdriver-http",
      "Use stackdriver in HTTP mode rather than GRPC (default). Requires a credentials file to be provided along with project ID"
    )
    .orFalse

  val stackdriverProjectOpt: Opts[Option[String]] =
    Opts.option[String]("stackdriver-project-id", "Google Project ID for use with Stackdriver").orNone

  val stackdriverCredentialsFileOpt: Opts[Option[String]] = Opts
    .option[String](
      "stackdriver-credentials-file",
      "Google service account credenitals file for publishing to stackdriver"
    )
    .orNone

  override def main: Opts[IO[ExitCode]] =
    (
      portOpt,
      collectorHostOpt,
      collectorPortOpt,
      jaegerUdpOpt,
      jaegerUdpHostOpt,
      jaegerUdpPortOpt,
      logOpt,
      otHostOpt,
      otPortOpt,
      stackdriverHttpOpt,
      stackdriverProjectOpt,
      stackdriverCredentialsFileOpt
    ).mapN(run)

  def run(
    port: Int,
    collectorHost: Option[String],
    collectorPort: Int,
    jaegerUdp: Boolean,
    jaegerUdpHost: String,
    jaegerUdpPort: Int,
    log: Boolean,
    otHost: Option[String],
    otPort: Int,
    stackdriverHttp: Boolean,
    stackdriverProject: Option[String],
    stackdriverCredentialsFile: Option[String]
  ): IO[ExitCode] =
    (for {
      blocker <- Blocker[IO]

      implicit0(logger: Logger[IO]) <- Resource.liftF(Slf4jLogger.create[IO])
      _ <- Resource.make(logger.info(s"Starting Trace 4 Cats Collector listening on tcp://::$port and udp://::$port"))(
        _ => logger.info("Shutting down Trace 4 Cats Collector")
      )

      collectorExporter <- collectorHost.traverse { host =>
        AvroSpanExporter.tcp[IO](blocker, host = host, port = collectorPort)
      }

      jaegerExporter <- if (jaegerUdp)
        JaegerSpanExporter[IO](blocker, host = jaegerUdpHost, port = jaegerUdpPort).map(Some(_))
      else Resource.pure[IO, Option[SpanExporter[IO]]](None)

      logExporter <- if (log) Resource.pure[IO, SpanExporter[IO]](LogExporter[IO]).map(Some(_))
      else Resource.pure[IO, Option[SpanExporter[IO]]](None)

      otExporter <- otHost.traverse { host =>
        OpenTelemetrySpanExporter[IO](blocker, host = host, port = otPort)
      }

      stackdriverExporter <- stackdriverProject.flatTraverse { projectId =>
        if (stackdriverHttp) stackdriverCredentialsFile.traverse { credsFile =>
          Resource.liftF(for {
            client <- Http4sJdkClient[IO](blocker)
            exporter <- StackdriverHttpSpanExporter[IO](projectId, credsFile, client)
          } yield exporter)
        } else StackdriverGrpcSpanExporter[IO](blocker, projectId = projectId).map(Some(_))
      }

      sink = Sink[IO](List(collectorExporter, jaegerExporter, logExporter, otExporter, stackdriverExporter).flatten: _*)

      tcp <- AvroServer.tcp[IO](blocker, sink, port)
      udp <- AvroServer.udp[IO](blocker, sink, port)
    } yield tcp.concurrently(udp)).use(_.compile.drain.as(ExitCode.Success))
}
