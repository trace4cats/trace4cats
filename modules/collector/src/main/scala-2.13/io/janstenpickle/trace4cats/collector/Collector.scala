package io.janstenpickle.trace4cats.collector

import cats.effect.{ExitCode, IO}
import cats.effect.kernel.{Async, Resource}
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._
import fs2.Chunk
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.collector.common.CommonCollector
import io.janstenpickle.trace4cats.collector.common.config.ConfigParser
import io.janstenpickle.trace4cats.collector.config.CollectorConfig
import io.janstenpickle.trace4cats.kernel.{BuildInfo, SpanExporter}
import io.janstenpickle.trace4cats.model.AttributeValue
import io.janstenpickle.trace4cats.opentelemetry.jaeger.OpenTelemetryJaegerSpanExporter
import io.janstenpickle.trace4cats.opentelemetry.otlp.OpenTelemetryOtlpGrpcSpanExporter
import io.janstenpickle.trace4cats.stackdriver.StackdriverGrpcSpanExporter

object Collector
    extends CommandIOApp(
      name = "trace4cats-collector",
      header = "Trace 4 Cats Collector",
      version = BuildInfo.version
    ) {

  override def main: Opts[IO[ExitCode]] =
    CommonCollector.configFileOpt.map { configFile =>
      Slf4jLogger.create[IO].flatMap { implicit logger =>
        (for {
          oth <- others[IO](configFile)
          stream <- CommonCollector[IO](configFile, oth)
        } yield stream).use(_.compile.drain.as(ExitCode.Success)).handleErrorWith { th =>
          logger.error(th)("Trace 4 Cats collector failed").as(ExitCode.Error)
        }
      }
    }

  def others[F[_]: Async](
    configFile: String
  ): Resource[F, List[(String, List[(String, AttributeValue)], SpanExporter[F, Chunk])]] =
    for {
      config <- Resource.eval(ConfigParser.parse[F, CollectorConfig](configFile))
      jaegerProtoExporters <- config.jaegerProto.traverse { jaeger =>
        OpenTelemetryJaegerSpanExporter[F, Chunk](jaeger.host, jaeger.port).map(
          (
            "Jaeger Proto",
            List[(String, AttributeValue)](
              "jaeger.protobuf.host" -> jaeger.host,
              "jaeger.protobuf.port" -> jaeger.port
            ),
            _
          )
        )
      }

      otGrpcExporters <- config.otlpGrpc.traverse { otlp =>
        OpenTelemetryOtlpGrpcSpanExporter[F, Chunk](host = otlp.host, port = otlp.port)
          .map(
            (
              "OpenTelemetry GRPC",
              List[(String, AttributeValue)]("otlp.grpc.host" -> otlp.host, "otlp.grpc.port" -> otlp.port),
              _
            )
          )
      }

      stackdriverExporters <- config.stackdriverGrpc.traverse { stackdriver =>
        StackdriverGrpcSpanExporter[F, Chunk](projectId = stackdriver.projectId)
          .map(
            (
              "Stackdriver GRPC",
              List[(String, AttributeValue)]("stackdriver.grpc.project.id" -> stackdriver.projectId),
              _
            )
          )
      }
    } yield List(jaegerProtoExporters, otGrpcExporters, stackdriverExporters).flatten

}
