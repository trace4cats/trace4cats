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
import io.janstenpickle.trace4cats.collector.common.{Http4sJdkClient, Sink}
import io.janstenpickle.trace4cats.jaeger.JaegerSpanExporter
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.log.LogExporter
import io.janstenpickle.trace4cats.strackdriver.StackdriverHttpSpanExporter

object CollectorLite
    extends CommandIOApp(
      name = "trace4cats-collector-lite",
      header = "Trace 4 Cats Collector Lite Edition",
      version = "0.1.0"
    ) {

  val portOpt: Opts[Int] =
    Opts
      .env[Int](CollectorPortEnv, help = "The port to run on.")
      .orElse(Opts.option[Int]("port", "The port to run on"))
      .withDefault(DefaultPort))

  val collectorHostOpt: Opts[Option[String]] =
    Opts
      .env[String](CollectorHostEnv, "Collector hostname to forward spans")
      .orElse(Opts.option[String]("collector", "Collector hostname"))
      .orNone

  val collectorPortOpt: Opts[Int] =
    Opts
      .env[Int](CollectorPortEnv, "Collector port to forward spans")
      .orElse(Opts.option[Int]("collector-port", "Collector port"))
      .withDefault(DefaultPort))

  val jaegerUdpOpt: Opts[Boolean] = Opts.flag("jaeger-udp", "Send spans via Jaeger UDP").orFalse
  val jaegerUdpPortOpt: Opts[Int] = Opts
    .option[Int]("jaeger-udp-port", "Jaeger UDP agent port")
    .withDefault(UdpSender.DEFAULT_AGENT_UDP_COMPACT_PORT))

  val jaegerUdpHostOpt: Opts[String] = Opts
    .option[String]("jaeger-udp-host", "Jaeger UDP agent host")
    .withDefault(UdpSender.DEFAULT_AGENT_UDP_HOST))

  val logOpt: Opts[Boolean] = Opts.flag("log", "Write spans to the log").orFalse

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

  val bufferSizeOpt: Opts[Int] =
    Opts
      .option[Int]("buffer-size", "Number of batches to buffer in case of network issues")
      .withDefault(500)

  override def main: Opts[IO[ExitCode]] =
    (
      portOpt,
      collectorHostOpt,
      collectorPortOpt,
      jaegerUdpOpt,
      jaegerUdpHostOpt,
      jaegerUdpPortOpt,
      logOpt,
      stackdriverProjectOpt,
      stackdriverCredentialsFileOpt,
      bufferSizeOpt
    ).mapN(run)

  def run(
    port: Int,
    collectorHost: Option[String],
    collectorPort: Int,
    jaegerUdp: Boolean,
    jaegerUdpHost: String,
    jaegerUdpPort: Int,
    log: Boolean,
    stackdriverProject: Option[String],
    stackdriverCredentialsFile: Option[String],
    bufferSize: Int
  ): IO[ExitCode] =
    (for {
      blocker <- Blocker[IO]

      implicit0(logger: Logger[IO]) <- Resource.liftF(Slf4jLogger.create[IO])
      _ <- Resource.make(
        logger.info(s"Starting Trace 4 Cats Collector Lite listening on tcp://::$port and udp://::$port")
      )(_ => logger.info("Shutting down Trace 4 Cats Collector Lite"))

      collectorExporter <- collectorHost.traverse { host =>
        AvroSpanExporter.tcp[IO](blocker, host = host, port = collectorPort).map("Trace4Cats Avro TCP" -> _)
      }

      jaegerExporter <- if (jaegerUdp)
        JaegerSpanExporter[IO](blocker, host = jaegerUdpHost, port = jaegerUdpPort).map(e => Some("Jaeger UDP" -> e))
      else Resource.pure[IO, Option[(String, SpanExporter[IO])]](None)

      logExporter <- if (log) Resource.pure[IO, SpanExporter[IO]](LogExporter[IO]).map(e => Some("Log" -> e))
      else Resource.pure[IO, Option[(String, SpanExporter[IO])]](None)

      stackdriverExporter <- (stackdriverProject, stackdriverCredentialsFile).mapN(_ -> _).traverse {
        case (projectId, credsFile) =>
          Resource.liftF(for {
            client <- Http4sJdkClient[IO](blocker)
            exporter <- StackdriverHttpSpanExporter[IO](projectId, credsFile, client)
          } yield "Stackdriver HTTP" -> exporter)
      }

      sink <- Sink[IO](bufferSize, List(collectorExporter, jaegerExporter, logExporter, stackdriverExporter).flatten)

      tcp <- AvroServer.tcp[IO](blocker, sink.pipe, port)
      udp <- AvroServer.udp[IO](blocker, sink.pipe, port)
    } yield tcp.concurrently(udp)).use(_.compile.drain.as(ExitCode.Success))
}
