# Trace4Cats ![](https://github.com/janstenpickle/trace4cats/.github/workflows/build.yml/badge.svg) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.janstenpickle/trace4cats-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.janstenpickle/trace4cats-core_2.13) [![Join the chat at https://gitter.im/trace4cats/community](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/trace4cats/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) 

Yet another distributed tracing system, this time just for Scala. Heavily relies upon
[Cats](https://typelevel.org/cats) and [Cats-effect](https://typelevel.org/cats-effect).

Compatible with [OpenTelemetry] and [Jaeger], based on, and interoperates wht [Natchez].

[Obligatory XKCD](https://xkcd.com/927/)

#### For release information and changes see [the changelog](CHANGELOG.md)

  * [Motivation](#motivation)
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
"io.janstenpickle" %% "trace4cats-core" % "0.6.0"
"io.janstenpickle" %% "trace4cats-inject" % "0.6.0"
"io.janstenpickle" %% "trace4cats-inject-zio" % "0.6.0"
"io.janstenpickle" %% "trace4cats-fs2" % "0.6.0"
"io.janstenpickle" %% "trace4cats-http4s-client" % "0.6.0"
"io.janstenpickle" %% "trace4cats-http4s-server" % "0.6.0"
"io.janstenpickle" %% "trace4cats-sttp-client" % "0.6.0"
"io.janstenpickle" %% "trace4cats-natchez" % "0.6.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.6.0"
"io.janstenpickle" %% "trace4cats-avro-kafka-exporter" % "0.6.0"
"io.janstenpickle" %% "trace4cats-avro-kafka-consumer" % "0.6.0"
"io.janstenpickle" %% "trace4cats-jaeger-thrift-exporter" % "0.6.0"
"io.janstenpickle" %% "trace4cats-log-exporter" % "0.6.0"
"io.janstenpickle" %% "trace4cats-opentelemetry-otlp-grpc-exporter" % "0.6.0"
"io.janstenpickle" %% "trace4cats-opentelemetry-otlp-http-exporter" % "0.6.0"
"io.janstenpickle" %% "trace4cats-opentelemetry-jaeger-exporter" % "0.6.0"
"io.janstenpickle" %% "trace4cats-stackdriver-grpc-exporter" % "0.6.0"
"io.janstenpickle" %% "trace4cats-stackdriver-http-exporter" % "0.6.0"
"io.janstenpickle" %% "trace4cats-datadog-http-exporter" % "0.6.0"
"io.janstenpickle" %% "trace4cats-newrelic-http-exporter" % "0.6.0"

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
[FS2 `EntryPoint`]: modules/fs2/src/main/scala/io/janstenpickle/trace4cats/fs2/Fs2EntryPoint.scala
[Http4s]: https://http4s.org/
[Jaeger]: https://www.jaegertracing.io/
[Log4Cats]: https://github.com/ChristopherDavenport/log4cats
[Natchez]: https://github.com/tpolecat/natchez
[`native-image`]: https://www.graalvm.org/docs/reference-manual/native-image/ 
[OpenTelemetry]: http://opentelemetry.io
[Stackdriver Trace]: https://cloud.google.com/trace/docs/reference
[Datadog]: https://docs.datadoghq.com/api/v1/tracing/
[NewRelic]: https://docs.newrelic.com/docs/understand-dependencies/distributed-tracing/trace-api/report-new-relic-format-traces-trace-api#new-relic-guidelines 
[`Resource`]: https://typelevel.org/cats-effect/datatypes/resource.html
[ZIO]: https://zio.dev
[Sttp]: https://sttp.softwaremill.com
