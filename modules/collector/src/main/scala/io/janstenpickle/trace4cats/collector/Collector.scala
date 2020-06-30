package io.janstenpickle.trace4cats.collector

import cats.effect.{Blocker, ExitCode, IO, Resource}
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.jaegertracing.thrift.internal.senders.UdpSender
import io.janstenpickle.trace4cats.avro._
import io.janstenpickle.trace4cats.avro.server.AvroServer
import io.janstenpickle.trace4cats.jaeger.JaegerSpanCompleter
import io.janstenpickle.trace4cats.kernel.SpanCompleter
import io.janstenpickle.trace4cats.log.LogCompleter
import io.janstenpickle.trace4cats.model.TraceProcess
import io.janstenpickle.trace4cats.opentelemetry.OpenTelemetrySpanCompleter

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

  final private val traceProcess = TraceProcess("trace4cats-collector")

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
      otPortOpt
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
    otPort: Int
  ): IO[ExitCode] =
    (for {
      blocker <- Blocker[IO]

      logger <- Resource.liftF(Slf4jLogger.create[IO])
      _ <- Resource.make(logger.info(s"Starting Trace 4 Cats Collector listening on tcp://::$port and udp://::$port"))(
        _ => logger.info("Shutting down Trace 4 Cats Collector")
      )

      collectorCompleter <- collectorHost.traverse { host =>
        AvroSpanCompleter.tcp[IO](blocker, traceProcess, host = host, port = collectorPort)
      }

      jaegerCompleter <- if (jaegerUdp)
        JaegerSpanCompleter[IO](blocker, traceProcess, host = jaegerUdpHost, port = jaegerUdpPort).map(Some(_))
      else Resource.pure[IO, Option[SpanCompleter[IO]]](None)

      logCompleter <- if (log) Resource.liftF(LogCompleter.create[IO]).map(Some(_))
      else Resource.pure[IO, Option[SpanCompleter[IO]]](None)

      otCompleter <- otHost.traverse { host =>
        OpenTelemetrySpanCompleter[IO](blocker, traceProcess, host = host, port = otPort)
      }

      sink = Sink[IO](List(collectorCompleter, jaegerCompleter, logCompleter, otCompleter).flatten: _*)

      tcp <- AvroServer.tcp[IO](blocker, sink, port)
      udp <- AvroServer.udp[IO](blocker, sink, port)
    } yield tcp.concurrently(udp)).use(_.compile.drain.as(ExitCode.Success))
}
