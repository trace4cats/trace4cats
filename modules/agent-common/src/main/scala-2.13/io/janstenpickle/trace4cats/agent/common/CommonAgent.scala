package io.janstenpickle.trace4cats.agent.common

import cats.effect.ExitCode
import cats.effect.kernel.{Async, Resource}
import cats.syntax.functor._
import cats.{Applicative, Parallel}
import com.monovore.decline.Opts
import fs2.{Chunk, Pipe}
import io.janstenpickle.trace4cats.`export`.QueuedSpanExporter
import io.janstenpickle.trace4cats.avro.server.AvroServer
import io.janstenpickle.trace4cats.avro.{AgentPortEnv, DefaultPort}
import io.janstenpickle.trace4cats.kernel.{BuildInfo, SpanExporter}
import io.janstenpickle.trace4cats.model.{AttributeValue, CompletedSpan}
import org.typelevel.log4cats.Logger

object CommonAgent {
  val portOpt: Opts[Int] =
    Opts
      .env[Int](AgentPortEnv, help = "The port to run on.")
      .orElse(Opts.option[Int]("port", "The port to run on"))
      .withDefault(DefaultPort)

  val bufferSizeOpt: Opts[Int] =
    Opts
      .env[Int]("T4C_AGENT_BUFFER_SIZE", "Number of batches to buffer in case of network issues")
      .orElse(Opts.option[Int]("buffer-size", "Number of batches to buffer in case of network issues"))
      .withDefault(500)

  val traceOpt: Opts[Boolean] =
    Opts
      .env[String]("T4C_AGENT_TRACE", "Enable tracing within the agent itself")
      .map {
        case "true" => true
        case _ => false
      }
      .orElse(Opts.flag("trace", "Enable tracing within the agent itself").orFalse)
      .withDefault(false)

  val traceSampleOpt: Opts[Option[Double]] =
    Opts
      .env[Double]("T4C_AGENT_TRACE_SAMPLE_RATE", "Peak rate (trace per second) at which agent traces may be sent")
      .orElse(
        Opts.option[Double]("agent-trace-sample-rate", "Peak rate (trace per second) at which agent traces may be sent")
      )
      .orNone

  def run[F[_]: Async: Parallel: Logger](
    port: Int,
    bufferSize: Int,
    exporterName: String,
    exporterAttributes: Map[String, AttributeValue],
    exporter: SpanExporter[F, Chunk],
    exporterText: String,
    trace: Boolean,
    traceRate: Option[Double]
  ): F[ExitCode] =
    (for {
      _ <- Resource.make(
        Logger[F]
          .info(s"Starting Trace 4 Cats Agent v${BuildInfo.version} on udp://::$port. Forwarding to $exporterText")
      )(_ => Logger[F].info("Shutting down Trace 4 Cats Agent"))

      pipeAndExp <-
        if (trace) AgentTrace[F](exporterName, exporterAttributes, port, traceRate, bufferSize, exporter)
        else
          Applicative[Resource[F, *]].pure[(Pipe[F, CompletedSpan, CompletedSpan], SpanExporter[F, Chunk])](
            (identity, exporter)
          )
      (pipe, exp) = pipeAndExp
      queuedExporter <- QueuedSpanExporter(bufferSize, List(exporterName -> exp))

      udpServer <- AvroServer.udp[F](pipe.andThen(queuedExporter.pipe), port)
    } yield udpServer).use(_.compile.drain.as(ExitCode.Success))
}
