package io.janstenpickle.trace4cats.agent

import cats.effect.{Blocker, ExitCode, IO, Resource}
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._
import fs2.Chunk
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.agent.common.CommonAgent
import io.janstenpickle.trace4cats.agent.common.CommonAgent._
import io.janstenpickle.trace4cats.avro._
import io.janstenpickle.trace4cats.kernel.BuildInfo

object Agent
    extends CommandIOApp(name = "trace4cats-agent", header = "Trace 4 Cats Agent", version = BuildInfo.version) {

  val collectorHostOpt: Opts[String] =
    Opts
      .env[String](CollectorHostEnv, "Collector hostname to forward spans")
      .orElse(Opts.option[String]("collector", "Collector hostname"))

  val collectorPortOpt: Opts[Int] =
    Opts
      .env[Int](CollectorPortEnv, "Collector port to forward spans")
      .orElse(Opts.option[Int]("collector-port", "Collector port"))
      .withDefault(DefaultPort)

  override def main: Opts[IO[ExitCode]] =
    (portOpt, collectorHostOpt, collectorPortOpt, bufferSizeOpt, traceOpt, traceSampleOpt).mapN(run)

  def run(
    port: Int,
    collectorHost: String,
    collectorPort: Int,
    bufferSize: Int,
    trace: Boolean,
    traceRate: Option[Double]
  ): IO[ExitCode] =
    (for {
      blocker <- Blocker[IO]
      implicit0(logger: Logger[IO]) <- Resource.liftF(Slf4jLogger.create[IO])

      avroExporter <-
        AvroSpanExporter
          .tcp[IO, Chunk](blocker, host = collectorHost, port = collectorPort)
    } yield CommonAgent.run[IO](
      blocker,
      port,
      bufferSize,
      "Avro TCP",
      Map("forward.host" -> collectorHost, "forward.port" -> collectorPort),
      avroExporter,
      s"tcp://$collectorHost:$collectorPort",
      trace,
      traceRate
    )).use(identity)

}
