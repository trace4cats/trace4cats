# Trace4Cats

[![GitHub Workflow Status](https://img.shields.io/github/workflow/status/trace4cats/trace4cats/Continuous%20Integration)](https://github.com/trace4cats/trace4cats/actions?query=workflow%3A%22Continuous%20Integration%22)
[![GitHub stable release](https://img.shields.io/github/v/release/trace4cats/trace4cats?label=stable&sort=semver)](https://github.com/trace4cats/trace4cats/releases)
[![GitHub latest release](https://img.shields.io/github/v/release/trace4cats/trace4cats?label=latest&include_prereleases&sort=semver)](https://github.com/trace4cats/trace4cats/releases)
[![Maven Central early release](https://img.shields.io/maven-central/v/io.janstenpickle/trace4cats-model_2.13?label=early)](https://maven-badges.herokuapp.com/maven-central/io.janstenpickle/trace4cats-model_2.13)
[![Join the chat at https://gitter.im/trace4cats/community](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/trace4cats/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

Yet another distributed tracing system, this time just for Scala. Heavily relies upon
[Cats] and [Cats Effect].

Compatible with [OpenTelemetry] and [Jaeger], based on, and interoperates with [Natchez].

[Obligatory XKCD](https://xkcd.com/927/)

#### For release information and changes see [the releases page](https://github.com/trace4cats/trace4cats/releases).

  * [Motivation](#motivation)
  * [Highlights](#highlights)
  * [Quickstart](#quickstart)
  * [Repositories](#repositories)
  * [Components](#components)
  * [Documentation](#documentation)
  * [SBT Dependencies](#sbt-dependencies)
  * [native-image Compatibility](#native-image-compatibility)
  * [Contributing](#contributing)

## Motivation

It increasingly seems that Java tracing libraries are dependent on [gRPC], which usually
brings along lots of other dependencies. You may find *Trace4Cats* useful if you want to...

- Reduce the number of dependencies in your application
- Resolve a dependency conflict caused by a tracing implementation
- Create a [`native-image`] using [GraalVM]

## Highlights

Trace4Cats supports publishing spans to the following systems:

- [Jaeger] via Thrift over UDP and Protobuf over gRPC
- [OpenTelemetry] via Protobuf over gRPC and JSON over HTTP
- Log using [Log4Cats]
- Trace4Cats Avro over UDP, TCP and Kafka
- [Google Cloud Trace] over HTTP and gRPC
- [Datadog] over HTTP
- [New Relic] over HTTP
- [Zipkin] over HTTP

Instrumentation for trace propagation and continuation is available for the following libraries:

- [Http4s] client and server
- [Sttp] client v3
- [Tapir]
- [FS2 Kafka] consumer and producer
- [FS2]

**Unlike other tracing libraries, trace attributes are lazily evaluated. If a span is not
[sampled](https://github.com/trace4cats/trace4cats-docs/blob/master/docs/sampling.md), no computation associated with
calculating attribute values will be performed.**

More information on how to use these can be found in the
[examples documentation](https://github.com/trace4cats/trace4cats-docs/blob/master/docs/examples.md).

## Quickstart

**For more see the [documentation](#documentation) and more advanced
[examples](https://github.com/trace4cats/trace4cats-docs/blob/master/docs/examples.md).**

Add the following dependencies to your `build.sbt`:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.13.1"
"io.janstenpickle" %% "trace4cats-inject" % "0.13.1"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.13.1"
```

Then run the [collector](https://github.com/trace4cats/trace4cats-docs/blob/master/docs/components.md#collectors) in
span logging mode:

```bash
echo "log-spans: true" > /tmp/collector.yaml
docker run -p7777:7777 -p7777:7777/udp -it \
  -v /tmp/collector.yaml:/tmp/collector.yaml \
  janstenpickle/trace4cats-collector-lite:0.13.1 \
  --config-file=/tmp/collector.yaml
```

Finally, run the following code to export some spans to the collector:

```scala
import cats.Monad
import cats.data.Kleisli
import cats.effect._
import cats.effect.std.Console
import cats.implicits._
import io.janstenpickle.trace4cats.Span
import io.janstenpickle.trace4cats.`export`.CompleterConfig
import io.janstenpickle.trace4cats.avro.AvroSpanCompleter
import io.janstenpickle.trace4cats.inject.{EntryPoint, Trace}
import io.janstenpickle.trace4cats.kernel.SpanSampler
import io.janstenpickle.trace4cats.model.{SpanKind, SpanStatus, TraceProcess}

import scala.concurrent.duration._

object Trace4CatsQuickStart extends IOApp.Simple {
  def entryPoint[F[_]: Async](process: TraceProcess): Resource[F, EntryPoint[F]] =
    AvroSpanCompleter.udp[F](process, config = CompleterConfig(batchTimeout = 50.millis)).map { completer =>
      EntryPoint[F](SpanSampler.always[F], completer)
    }

  def runF[F[_]: Monad: Console: Trace]: F[Unit] =
    for {
      _ <- Trace[F].span("span1")(Console[F].println("trace this operation"))
      _ <- Trace[F].span("span2", SpanKind.Client)(Console[F].println("send some request"))
      _ <- Trace[F].span("span3", SpanKind.Client)(
        Trace[F].putAll("attribute1" -> "test", "attribute2" -> 200) >>
          Trace[F].setStatus(SpanStatus.Cancelled)
      )
    } yield ()

  def run: IO[Unit] =
    entryPoint[IO](TraceProcess("trace4cats")).use { ep =>
      ep.root("this is the root span").use { span =>
        runF[Kleisli[IO, Span[IO], *]].run(span)
      }
    }
}
```

## Repositories

Trace4Cats is separated into a few repositories:

- [`trace4cats-avro`](https://github.com/trace4cats/trace4cats-avro) [Avro] codecs, TCP/UDP server and exporter
- [`trace4cats-avro-kafka`](https://github.com/trace4cats/trace4cats-avro-kafka) [Avro] [Kafka] exporter and consumer
- [`trace4cats-cloudtrace`](https://github.com/trace4cats/trace4cats-cloudtrace) [Google Cloud Trace] exporters
- [`trace4cats-components`](https://github.com/trace4cats/trace4cats-components) standalone Trace4Cats
  [components](#components)
- [`trace4cats-datadog`](https://github.com/trace4cats/trace4cats-datadog) [Datadog] exporters
- [`trace4cats-docs`](https://github.com/trace4cats/trace4cats-docs) documentation and examples
- [`trace4cats-dynamic-extras`](https://github.com/trace4cats/trace4cats-dynamic-extras) extra utilities for dynamically
  configuring Trace4Cats
- [`trace4cats-exporter-http`](https://github.com/trace4cats/trace4cats-exporter-http) HTTP span exporter
- [`trace4cats-http4s`](https://github.com/trace4cats/trace4cats-http4s) [Http4s] client and server integrations
- [`trace4cats-jaeger`](https://github.com/trace4cats/trace4cats-jaeger) [Jaeger] exporters
- [`trace4cats-jaeger-integration-test`](https://github.com/trace4cats/trace4cats-jaeger-integration-test) Integration
  test for exporters to [Jaeger]
- [`trace4cats-kafka`](https://github.com/trace4cats/trace4cats-kafka) [FS2 Kafka] integrations
- [`trace4cats-natchez`](https://github.com/trace4cats/trace4cats-natchez) [Natchez] integrations
- [`trace4cats-newrelic`](https://github.com/trace4cats/trace4cats-newrelic) [New Relic] exporters
- [`trace4cats-opentelemetry`](https://github.com/trace4cats/trace4cats-opentelemetry) [OpenTelemetry] exporters
- [`trace4cats-sttp`](https://github.com/trace4cats/trace4cats-sttp) [Sttp] client and [Tapir] integrations
- [`trace4cats-tail-sampling-extras`](https://github.com/trace4cats/trace4cats-tail-sampling-extras) extra utilities for
  tail sampling
- [`trace4cats-zipkin`](https://github.com/trace4cats/trace4cats-zipkin) [Zipkin] exporters
- [`trace4cats-zio`](https://github.com/trace4cats/trace4cats-zio) [ZIO] implementations of Trace4Cats typeclasses

## Components

Trace4Cats is made up as both a set of libraries for integration in applications and standalone processes. For
information on the libraries and interfaces see the [design documentation](https://github.com/trace4cats/trace4cats-docs/blob/master/docs/design.md).

The standalone components are the agent and the collector. To see how they work together, see the
[topologies documentation](https://github.com/trace4cats/trace4cats-docs/blob/master/docs/topologies.md), for information on configuring and running the agent and collector see
the [components documentation](https://github.com/trace4cats/trace4cats-docs/blob/master/docs/components.md).

The source code for these components is located in the
[`trace4cats-components`](https://github.com/trace4cats/trace4cats-components) repository.

## Documentation

- [Design](https://github.com/trace4cats/trace4cats-docs/blob/master/docs/design.md) - Trace4Cats design
- [Components](https://github.com/trace4cats/trace4cats-docs/blob/master/docs/components.md) - running and configuring
  Trace4Cats components
- [Topologies](https://github.com/trace4cats/trace4cats-docs/blob/master/docs/topologies.md) - information on potential
  Trace4Cats deployment topologies
- [Sampling](https://github.com/trace4cats/trace4cats-docs/blob/master/docs/sampling.md) - trace sampling
- [Filtering](https://github.com/trace4cats/trace4cats-docs/blob/master/docs/filtering.md) - span attribute filtering
- [Examples](https://github.com/trace4cats/trace4cats-docs/blob/master/docs/examples.md) - code usage examples

## SBT Dependencies

To use Trace4Cats within your application add the dependencies listed below as needed:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.13.1"
"io.janstenpickle" %% "trace4cats-inject" % "0.13.1"
"io.janstenpickle" %% "trace4cats-inject-zio" % "0.13.1"
"io.janstenpickle" %% "trace4cats-rate-sampling" % "0.13.1"
"io.janstenpickle" %% "trace4cats-fs2" % "0.13.1"
"io.janstenpickle" %% "trace4cats-http4s-client" % "0.13.1"
"io.janstenpickle" %% "trace4cats-http4s-server" % "0.13.1"
"io.janstenpickle" %% "trace4cats-sttp-client3" % "0.13.1"
"io.janstenpickle" %% "trace4cats-sttp-tapir" % "0.13.1"
"io.janstenpickle" %% "trace4cats-natchez" % "0.13.1"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.13.1"
"io.janstenpickle" %% "trace4cats-avro-kafka-exporter" % "0.13.1"
"io.janstenpickle" %% "trace4cats-avro-kafka-consumer" % "0.13.1"
"io.janstenpickle" %% "trace4cats-jaeger-thrift-exporter" % "0.13.1"
"io.janstenpickle" %% "trace4cats-log-exporter" % "0.13.1"
"io.janstenpickle" %% "trace4cats-opentelemetry-otlp-grpc-exporter" % "0.13.1"
"io.janstenpickle" %% "trace4cats-opentelemetry-otlp-http-exporter" % "0.13.1"
"io.janstenpickle" %% "trace4cats-opentelemetry-jaeger-exporter" % "0.13.1"
"io.janstenpickle" %% "trace4cats-stackdriver-grpc-exporter" % "0.13.1"
"io.janstenpickle" %% "trace4cats-stackdriver-http-exporter" % "0.13.1"
"io.janstenpickle" %% "trace4cats-datadog-http-exporter" % "0.13.1"
"io.janstenpickle" %% "trace4cats-newrelic-http-exporter" % "0.13.1"
"io.janstenpickle" %% "trace4cats-zipkin-http-exporter" % "0.13.1"
```

## [`native-image`] Compatibility

The following span completers have been found to be compatible with [`native-image`]:

- Trace4Cats Avro
- [Jaeger] Thrift over UDP
- [OpenTelemetry] JSON over HTTP
- Log
- [Google Cloud Trace] over HTTP
- [Datadog] over HTTP
- [New Relic] over HTTP
- [Zipkin] over HTTP

## Contributing

This project supports the [Scala Code of Conduct](https://typelevel.org/code-of-conduct.html) and aims that its channels
(mailing list, Gitter, github, etc.) to be welcoming environments for everyone.


[Avro]: https://avro.apache.org
[Kafka]: https://kafka.apache.org
[FS2]: https://fs2.io/
[Http4s]: https://http4s.org/
[Jaeger]: https://www.jaegertracing.io/
[Log4Cats]: https://github.com/typelevel/log4cats
[Natchez]: https://github.com/tpolecat/natchez
[`native-image`]: https://www.graalvm.org/docs/reference-manual/native-image/
[OpenTelemetry]: http://opentelemetry.io
[Google Cloud Trace]: https://cloud.google.com/trace/docs/reference
[Datadog]: https://docs.datadoghq.com/api/v1/tracing/
[New Relic]: https://docs.newrelic.com/docs/understand-dependencies/distributed-tracing/trace-api/report-new-relic-format-traces-trace-api#new-relic-guidelines
[ZIO]: https://zio.dev
[Sttp]: https://sttp.softwaremill.com
[Tapir]: https://tapir.softwaremill.com
[FS2 Kafka]: https://fd4s.github.io/fs2-kafka/
[Zipkin]: https://zipkin.io
[GraalVM]: https://www.graalvm.org
[gRPC]: https://grpc.io
[Cats]: https://typelevel.org/cats
[Cats Effect]: https://typelevel.org/cats-effect
