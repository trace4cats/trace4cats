package io.janstenpickle.trace4cats.collector

import cats.Parallel
import cats.data.Kleisli
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, ExitCode, IO, Resource, Timer}
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.effect._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.collector.common.CommonCollector
import io.janstenpickle.trace4cats.kernel.SpanExporter
import io.janstenpickle.trace4cats.opentelemetry.jaeger.OpenTelemetryJaegerSpanExporter
import io.janstenpickle.trace4cats.opentelemetry.otlp.OpenTelemetryOtlpGrpcSpanExporter
import io.janstenpickle.trace4cats.stackdriver.StackdriverGrpcSpanExporter

object Collector
    extends CommandIOApp(name = "trace4cats-collector", header = "Trace 4 Cats Collector", version = "0.1.0") {

  val otlpGrpcHostOpt: Opts[Option[String]] =
    Opts.option[String]("opentelemetry-otlp-grpc-host", "Write spans via OpenTelemetry OLTP protobufs format").orNone
  val otlpGrpcPortOpt: Opts[Int] =
    Opts.option[Int]("opentelemetry-otpl-grpc-port", "OpenTelelmetry OLTP protobufs port").withDefault(55680)

  val jaegerProtoHostOpt: Opts[Option[String]] =
    Opts.option[String]("jaeger-proto-host", "Write spans via Jaeger protobufs format").orNone
  val jaegerProtoPortOpt: Opts[Int] =
    Opts.option[Int]("jaeger-proto-port", "Jaeger protobufs port").withDefault(14250)

  override def main: Opts[IO[ExitCode]] =
    (CommonCollector[IO], othersOpts[IO]).mapN { (common, others) =>
      Blocker[IO].use { blocker =>
        for {
          logger <- Slf4jLogger.create[IO]
          exitCode <- others
            .run(blocker)
            .flatMap { oth =>
              common.run((blocker, logger, oth))
            }
            .use(_.compile.drain.as(ExitCode.Success))
        } yield exitCode
      }
    }

  def othersOpts[F[_]: ConcurrentEffect: Parallel: ContextShift: Timer]: Opts[
    Kleisli[Resource[F, *], Blocker, List[(String, SpanExporter[F])]]
  ] =
    (
      jaegerProtoHostOpt,
      jaegerProtoPortOpt,
      otlpGrpcHostOpt,
      otlpGrpcPortOpt,
      CommonCollector.stackdriverHttpOpt,
      CommonCollector.stackdriverProjectOpt
    ).mapN(others[F])

  def others[F[_]: ConcurrentEffect: Parallel: ContextShift: Timer](
    jaegerProtoHost: Option[String],
    jaegerProtoPort: Int,
    otGrpcHost: Option[String],
    otGrpcPort: Int,
    stackdriverHttp: Boolean,
    stackdriverProject: Option[String]
  ): Kleisli[Resource[F, *], Blocker, List[(String, SpanExporter[F])]] = Kleisli { blocker =>
    for {
      jaegerProtoExporter <- jaegerProtoHost.traverse { host =>
        OpenTelemetryJaegerSpanExporter[F](host, jaegerProtoPort).map("Jaeger Proto" -> _)
      }

      otGrpcExporter <- otGrpcHost.traverse { host =>
        OpenTelemetryOtlpGrpcSpanExporter[F](host = host, port = otGrpcPort).map("OpenTelemetry GRPC" -> _)
      }

      stackdriverExporter <- stackdriverProject.flatTraverse { projectId =>
        if (!stackdriverHttp)
          StackdriverGrpcSpanExporter[F](blocker, projectId = projectId).map(e => Some("Stackdriver GRPC" -> e))
        else Resource.pure[F, Option[(String, SpanExporter[F])]](None)
      }
    } yield List(jaegerProtoExporter, otGrpcExporter, stackdriverExporter).flatten
  }

}
