# Trace4Cats

[![Build & Release](https://github.com/janstenpickle/trace4cats/workflows/Build%20&%20Release/badge.svg)](https://github.com/janstenpickle/trace4cats/actions?query=workflow%3A%22Build+%26+Release%22)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.janstenpickle/trace4cats-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.janstenpickle/trace4cats-core_2.13)
[![Join the chat at https://gitter.im/trace4cats/community](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/trace4cats/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

Yet another distributed tracing system, this time just for Scala. Heavily relies upon
[Cats](https://typelevel.org/cats) and [Cats-effect](https://typelevel.org/cats-effect).

Compatible with [OpenTelemetry] and [Jaeger], based on, and interoperates wht [Natchez].

[Obligatory XKCD](https://xkcd.com/927/)

#### For release information and changes see [the releases page](releases)

  * [Motivation](#motivation)
  * [Highlights](#highlights)
  * [Quickstart](#quickstart)
  * [Components](#components)
  * [Documentation](#documentation)
  * [SBT Dependencies](#sbt-dependencies)
  * [native-image Compatibility](#native-image-compatibility)
  * [Contributing](#contributing)

## Motivation

It increasingly seems that Java tracing libraries are dependent on GRPC, which usually
brings along lots of other dependencies. You may find *Trace4Cats* useful if you want to...

- Reduce the number of dependencies in your application
- Resolve a dependency conflict caused by a tracing implementation
- Create a [`native-image`] using [Graalvm](https://www.graalvm.org/)

## Highlights

Trace4Cats supports publishing spans to the following systems:

- [Jaeger] via Thrift over UDP and Protobufs over GRPC
- [OpenTelemetry] via Protobufs over GRPC and JSON over HTTP
- Log using [Log4Cats]
- Trace4Cats Avro over UDP, TCP and Kafka
- [Stackdriver Trace] over HTTP and GRPC
- [Datadog] over HTTP
- [NewRelic] over HTTP
- [Zipkin] over HTTP

Instrumentation for trace propagation and continuation is available for the following libraries

- [Http4s] client and server
- [Sttp] client v3
- [Tapir]
- [FS2 Kafka] consumer and producer
- [FS2]

**Unlike other tracing libraries, trace attributes are lazily evaluated. If a span is not [sampled](docs/sampling.md),
no computation associated with calculating attribute values will be performed**

For more information on how to use these can be found in the [examples documentation](docs/examples.md)

## Quickstart

**For more see the [documentation](#documentation) for more advanced [examples](docs/examples.md)**

Add the following dependencies to your `build.sbt`:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.11.0"
"io.janstenpickle" %% "trace4cats-inject" % "0.11.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.11.0"
```

Then run the [collector](docs/components.md#collectors) in span logging mode:

```bash
echo "log-spans: true" > /tmp/collector.yaml
docker run -p7777:7777 -p7777:7777/udp -it \
  -v /tmp/collector.yaml:/tmp/collector.yaml \
  janstenpickle/trace4cats-collector-lite:0.11.0 \
  --config-file=/tmp/collector.yaml
```

Finally, run the following code to export some spans to the collector:

```scala
import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.`export`.CompleterConfig
import io.janstenpickle.trace4cats.avro.AvroSpanCompleter
import io.janstenpickle.trace4cats.inject.{EntryPoint, Trace}
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SpanKind, SpanStatus, TraceProcess}

import scala.concurrent.duration._

object Trace4CatsQuickStart extends IOApp {
  def entryPoint[F[_]: Async: Logger](process: TraceProcess): Resource[F, EntryPoint[F]] =
    AvroSpanCompleter.udp[F](process, config = CompleterConfig(batchTimeout = 50.millis)).map { completer =>
      EntryPoint[F](SpanSampler.always[F], completer)
    }

  def runF[F[_]: Sync: Trace]: F[Unit] =
    for {
      _ <- Trace[F].span("span1")(Sync[F].delay(println("trace this operation")))
      _ <- Trace[F].span("span2", SpanKind.Client)(Sync[F].delay(println("send some request")))
      _ <- Trace[F].span("span3", SpanKind.Client)(
        Trace[F].putAll("attribute1" -> "test", "attribute2" -> 200).flatMap { _ =>
          Trace[F].setStatus(SpanStatus.Cancelled)
        }
      )
    } yield ()

  override def run(args: List[String]): IO[ExitCode] =
    (for {
      implicit0(logger: Logger[IO]) <- Resource.eval(Slf4jLogger.create[IO])
      ep <- entryPoint[IO](TraceProcess("trace4cats"))
    } yield ep)
      .use { ep =>
        ep.root("this is the root span").use { span =>
          runF[Kleisli[IO, Span[IO], *]].run(span)
        }
      }
      .as(ExitCode.Success)
}
```

## Components

Trace4Cats is made up as both a set of libraries for integration in applications and standalone processes. For
information on the libraries and interfaces see the [design documentation](docs/design.md).

The standalone components are the agent and the collector. To see how they work together, see the
[topologies documentation](docs/topologies.md), for information on configuring and running the agent and collector see
the [components documentation](docs/components.md).

## Documentation

- [Design](docs/design.md) - Trace4Cats design
- [Components](docs/components.md) - running and configuring Trace4Cats components
- [Topologies](docs/topologies.md) - information on potential Trace4Cats deployment topologies
- [Sampling](docs/sampling.md) - trace sampling
- [Filtering](docs/filtering.md) - span attribute filtering
- [Examples](docs/examples.md) - code usage examples

## SBT Dependencies

To use Trace4Cats within your application add the dependencies listed below as needed:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.11.0"
"io.janstenpickle" %% "trace4cats-inject" % "0.11.0"
"io.janstenpickle" %% "trace4cats-inject-zio" % "0.11.0"
"io.janstenpickle" %% "trace4cats-rate-sampling" % "0.11.0"
"io.janstenpickle" %% "trace4cats-fs2" % "0.11.0"
"io.janstenpickle" %% "trace4cats-http4s-client" % "0.11.0"
"io.janstenpickle" %% "trace4cats-http4s-server" % "0.11.0"
"io.janstenpickle" %% "trace4cats-sttp-client3" % "0.11.0"
"io.janstenpickle" %% "trace4cats-sttp-tapir" % "0.11.0"
"io.janstenpickle" %% "trace4cats-natchez" % "0.11.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.11.0"
"io.janstenpickle" %% "trace4cats-avro-kafka-exporter" % "0.11.0"
"io.janstenpickle" %% "trace4cats-avro-kafka-consumer" % "0.11.0"
"io.janstenpickle" %% "trace4cats-jaeger-thrift-exporter" % "0.11.0"
"io.janstenpickle" %% "trace4cats-log-exporter" % "0.11.0"
"io.janstenpickle" %% "trace4cats-opentelemetry-otlp-grpc-exporter" % "0.11.0"
"io.janstenpickle" %% "trace4cats-opentelemetry-otlp-http-exporter" % "0.11.0"
"io.janstenpickle" %% "trace4cats-opentelemetry-jaeger-exporter" % "0.11.0"
"io.janstenpickle" %% "trace4cats-stackdriver-grpc-exporter" % "0.11.0"
"io.janstenpickle" %% "trace4cats-stackdriver-http-exporter" % "0.11.0"
"io.janstenpickle" %% "trace4cats-datadog-http-exporter" % "0.11.0"
"io.janstenpickle" %% "trace4cats-newrelic-http-exporter" % "0.11.0"

```

## [`native-image`] Compatibility

The following span completers have been found to be compatible with [`native-image`]:

- Trace4Cats Avro
- [Jaeger] Thrift over UDP
- [OpenTelemetry] JSON over HTTP
- Log
- [Stackdriver Trace] over HTTP
- [Datadog] over HTTP
- [NewRelic] over HTTP

## Contributing

This project supports the [Scala Code of Conduct](https://typelevel.org/code-of-conduct.html) and aims that its channels
(mailing list, Gitter, github, etc.) to be welcoming environments for everyone.


[FS2]: https://fs2.io/
[Http4s]: https://http4s.org/
[Jaeger]: https://www.jaegertracing.io/
[Log4Cats]: https://github.com/typelevel/log4cats
[Natchez]: https://github.com/tpolecat/natchez
[`native-image`]: https://www.graalvm.org/docs/reference-manual/native-image/
[OpenTelemetry]: http://opentelemetry.io
[Stackdriver Trace]: https://cloud.google.com/trace/docs/reference
[Datadog]: https://docs.datadoghq.com/api/v1/tracing/
[NewRelic]: https://docs.newrelic.com/docs/understand-dependencies/distributed-tracing/trace-api/report-new-relic-format-traces-trace-api#new-relic-guidelines
[`Resource`]: https://typelevel.org/cats-effect/datatypes/resource.html
[ZIO]: https://zio.dev
[Sttp]: https://sttp.softwaremill.com
[Tapir]: https://tapir.softwaremill.com
[FS2 Kafka]: https://fd4s.github.io/fs2-kafka/
[Zipkin]: https://zipkin.io