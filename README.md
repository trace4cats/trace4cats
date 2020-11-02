# Trace4Cats ![](https://github.com/janstenpickle/trace4cats/.github/workflows/build.yml/badge.svg) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.janstenpickle/trace4cats-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.janstenpickle/trace4cats-core_2.13) [![Join the chat at https://gitter.im/trace4cats/community](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/trace4cats/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) 

Yet another distributed tracing system, this time just for Scala. Heavily relies upon
[Cats](https://typelevel.org/cats) and [Cats-effect](https://typelevel.org/cats-effect).

Compatible with [OpenTelemetry] and [Jaeger], based on, and interoperates wht [Natchez].

[Obligatory XKCD](https://xkcd.com/927/)

#### For release information and changes see [the changelog](CHANGELOG.md)

  * [Motivation](#motivation)
  * [Design](#design)
     * [Trace Injection](#trace-injection)
     * [Interfaces](#interfaces)
        * [SpanExporter and SpanCompleter](#spanexporter-and-spancompleter)
        * [SpanSampler](#spansampler)
        * [ToHeaders](#toheaders)
  * [Components](#components)
     * [Agent](#agent)
        * [Running](#running)
      * [Agent Kafka](#agent-kafka)
         * [Running](#running-1)
     * [Collector](#collector)
        * [Configuring](#configuring)
        * [Running](#running-2)
     * [Collector Lite](#collector-lite)
        * [Configuring](#configuring-1)
        * [Running](#running-3)
  * [SBT Dependencies](#sbt-dependencies)
  * [Code Examples](#code-examples)
     * [Simple](#simple)
     * [Simple ZIO](#simple-zio)
     * [Advanced](#advanced)
     * [Inject](#inject)
     * [Inject ZIO](#inject-zio)
     * [Natchez](#natchez)
     * [FS2](#fs2)
     * [Http4s](#http4s)
     * [Http4s ZIO](#http4s-zio)
     * [Sttp](#http4s)
     * [Sttp ZIO](#http4s-zio)     
  * [native-image Compatibility](#native-image-compatibility)
  * [Contributing](#contributing)
  * [TODO](#todo)

## Motivation

It increasingly seems that Java tracing libraries are dependent on GRPC, which usually
brings along lots of other dependencies. You may find *Trace4Cats* useful if you want to...

- Reduce the number of dependencies in your application
- Resolve a dependency conflict caused by a tracing implementation
- Create a [`native-image`] using [Graalvm](https://www.graalvm.org/)
  
## Design

Trace 4 Cats partially implements [OpenTelemetry] tracing, just enough
traces can be exported to [Jaeger] or the 
[OpenTelemetry collector](https://opentelemetry.io/docs/collector/about/). 

## Trace Injection

Based heavily on [Natchez] but exposes the specific Trace4Cats functionality of setting the span
kind and status. A `Trace` typeclass is used to propagate a span context throughout the callstack.

See the [example](#inject) below for more information, usage, and interoperability with [Natchez].

### Interfaces

The following interfaces allow different backends to be plugged in and may adjust how traces
are sampled.

#### `SpanExporter` and `SpanCompleter`
`SpanExporter`s are used to forward a batch of spans to as certain location in a certain format.
Multiple implementations may be combined using the provided `Monoid` instance.

`SpanCompleter`s are used when a spans is finished. They will usually delegate to a `SpanExporter`
of the same format. Multiple implementations may be combined using the provided `Monoid` instance.

`SpanCompleter`s should generally buffer spans in a circular buffer so that completing a span should
be non-blocking for the hosting application. `SpanExporter`s may be blocking, however a buffering
wrapper implementation is available, which is used in the Collectors to provide non-blocking behaviour 
when accepting new spans. 

The following implementations are provided out of the box:

- [Jaeger] agent via Thrift over UDP and Protobufs over GRPC
- [OpenTelemetry] collector via Protobufs over GRPC and JSON over HTTP
- Log using [Log4Cats]
- Trace4Cats Avro over TCP, UDP or Kafka
- [Stackdriver Trace] over HTTP or GRPC
- [Datadog] over HTTP
- [NewRelic] over HTTP

#### `SpanSampler`
Used to decide whether or not a span should be sampled.

The following implementations are provided out of the box:

- Always
- Never
- Probabilistic

#### `ToHeaders`

Convert a span context to and from message or http headers.

The following implementations are provided out of the box:

- [W3C Trace Context](https://www.w3.org/TR/trace-context/)

## Components

### Agent

A lightweight Avro UDP server, built with [`native-image`] designed to be co-located with a traced
application. Forwards batches of spans onto the Collector over TCP.

#### Running

```bash
docker run -it janstenpickle/trace4cats-agent:0.6.0
```

### Agent Kafka

A lightweight Avro UDP server, built with [`native-image`] designed to be co-located with a traced
application. Forwards batches of spans onto a supplied Kafka topic.

#### Running

```bash
docker run -it janstenpickle/trace4cats-agent-kafka:0.6.0
```

### Collector

A standalone server designed to forward spans via various `SpanExporter` implementations. Currently
the Collector supports the following exporters:

- [Jaeger] via Thrift over UDP and Protobufs over GRPC
- [OpenTelemetry] via Protobufs over GRPC and JSON over HTTP
- Log using [Log4Cats]
- Trace4Cats Avro over TCP
- Trace4Cats Avro over Kafka
- [Stackdriver Trace] over HTTP and GRPC
- [Datadog] over HTTP
- [NewRelic] over HTTP

#### Configuring

The Collector is configured using YAML or JSON, all configuration is optional, however you should probably configure
at least one exporter, set up forwarding or enable span logging.

See Kafka documentation for additional [Kafka consumer config] and [Kafka producer config]

```yaml
listener:
  port: 7779 # Change the default lister port

log-spans: true # Log spans to the console, defaults to false

buffer-size: 1000 # How many batches to buffer in case of a slow exporter, defaults to 500

# Span sampling
sampling:
  sample-probability: 0.05 # Optional - must be between 0 and 0.1. 0.1 being always sample, and 0.0 being never
  span-names: some-span-name # Optional - name of spans to sample (may be partial match)
  cache-ttl-minutes: 10 # Cache duration for sample decision, defaults to 2 mins
  max-cache-size: 500000 # Max number of entries in the sample decision cache, defaults to 1000000

# Optional attribute filtering
attribute-filtering:
  names:
    - some.attribute.name
    - another.attribute.name
  values:
    - prohibited
  name-values:
    some.attribute.name: prohibited

# Listen for spans on a Kafka topic
kafka-listener:
  group: trace4cats-collector
  topic: spans
  bootstrap-servers:
    - "localhost:9092"
  # Optional Kafka batching within collector
  batch:
    size: 1000 # Maximum number of spans within a batch from kafka
    timeout-seconds: 10 # How long to linger if batch is < configured size
  # Optional additional Kafka consumer config
  consumer-config:
    key: value

# Forward spans to another collector
forwarder:
  host: some-remote-host
  port: 7777

# Forward spans to a kafka topic
kafka-forwarder:
  topic: spans
  bootstrap-servers:
    - "localhost:9092"  
  # Optional additional Kafka producer config
  producer-config:
    key: value

# Export to Jaeger
jaeger:
  host: jaeger-host
  port: 9999 # Defaults to 6831

# Export to Jaeger via protbufs
jaeger-proto:
  host: jaeger-host
  port: 9999 # Defaults to 14250

# Export to OpenTelemetry Collector via HTTP
otlp-http:
  host: otlp-host
  port: 9999 # Defaults to 55681

# Export to OpenTelemetry Collector via GRPC
otlp-grpc
  host: otlp-host
  port: 9999 # Defaults to 55680

# Export to Stackdriver via HTTP
# All config is optional, if running in GCP they will be obtained from the metadata endpoint
stackdriver-http:
  project-id: some-project-id
  credentials-file: /path/to/credentials.json
  service-account-name: svcacc2 # Defaults to 'default'

# Export to Stackdriver via GRPC
stackdriver-grpc:
  project-id: some-project-id

# Export to Datadog agent - All config is optional
datadog:
  host: agent-host # defaults to 'localhost'
  port: 9999 # defaults to 8126

# Export to NewRelic
new-relic:
  api-key: 7c1989d1-e019-46bc-a04e-824fdf33b237
  endpoint: EU # defaults to US, may be a custom endpoint
```

#### Running

```bash
docker run -p7777:7777 -p7777:7777/udp -it -v /path/to/your/collector-config.yaml:/tmp/collector.yaml janstenpickle/trace4cats-collector:0.6.0 --config-file=/tmp/collector.yaml
```

### Collector Lite

Similar implementation to the Collector, but compiled with [`native-image`] so does not support any
GRPC based exporters. Currently Collector lite supports the following exporters:

- [Jaeger] via Thrift over UDP
- [OpenTelemetry] via JSON over HTTP
- Log using [Log4Cats]
- Trace4Cats Avro over TCP
- Trace4Cats Avro over Kafka
- [Stackdriver Trace] over HTTP
- [Datadog] over HTTP
- [NewRelic] over HTTP

#### Configuring

As with the [Collector](#collector), Collector Lite is configured using YAML or JSON, however not all exporters are
supported

```yaml
listener:
  port: 7779 # Change the default lister port

log-spans: true # Log spans to the console, defaults to false

buffer-size: 1000 # How many batches to buffer in case of a slow exporter, defaults to 500

# Span sampling
sampling:
  sample-probability: 0.05 # Optional - must be between 0 and 0.1. 0.1 being always sample, and 0.0 being never
  span-names: some-span-name # Optional - name of spans to sample (may be partial match)
  cache-ttl-minutes: 10 # Cache duration for sample decision, defaults to 2 mins
  max-cache-size: 500000 # Max number of entries in the sample decision cache, defaults to 1000000

# Optional attribute filtering
attribute-filtering:
  names:
    - some.attribute.name
    - another.attribute.name
  values:
    - prohibited
  name-values:
    some.attribute.name: prohibited

# Listen for spans on a Kafka topic
kafka-listener:
  group: trace4cats-collector
  topic: spans
  bootstrap-servers:
    - "localhost:9092"
  # Optional Kafka batching within collector
  batch:
    size: 1000 # Maximum number of spans within a batch from kafka
    timeout-seconds: 10 # How long to linger if batch is < configured size
  # Optional additional Kafka consumer config
  consumer-config:
    key: value

# Forward spans to another collector
forwarder:
  host: some-remote-host
  port: 7777

# Forward spans to a kafka topic
kafka-forwarder:
  topic: spans
  bootstrap-servers:
    - "localhost:9092"  
  # Optional additional Kafka producer config
  producer-config:
    key: value

# Export to Jaeger
jaeger:
  host: jaeger-host
  port: 9999 # Defaults to 6831

# Export to OpenTelemetry Collector via HTTP
otlp-http:
  host: otlp-host
  port: 9999 # Defaults to 55681

# Export to Stackdriver via HTTP
# All config is optional, if running in GCP they will be obtained from the metadata endpoint
stackdriver-http:
  project-id: some-project-id
  credentials-file: /path/to/credentials.json
  service-account-name: svcacc2 # Defaults to 'default'

# Export to Datadog agent - All config is optional
datadog:
  host: agent-host # defaults to 'localhost'
  port: 9999 # defaults to 8126

# Export to NewRelic
new-relic:
  api-key: 7c1989d1-e019-46bc-a04e-824fdf33b237
  endpoint: EU # defaults to US, may be a custom endpoint
```

#### Running

```bash
docker run -p7777:7777 -p7777:7777/udp -it -v /path/to/your/collector-config.yaml:/tmp/collector.yaml janstenpickle/trace4cats-collector-lite:0.6.0 --config-file=/tmp/collector.yaml
```

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

## Code Examples

### [Simple](modules/example/src/main/scala/io/janstenpickle/trace4cats/example/SimpleExample.scala)

This example shows a simple example of how to use Trace4Cats without the need to 
inject a root span via a Kleisli (see the Inject example below).

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.6.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.6.0"

```

### [Simple ZIO](modules/example/src/main/scala/io/janstenpickle/trace4cats/example/SimpleZioExample.scala)

This example shows a simple example of how to use Trace4Cats with ZIO without the need to 
inject a root span via the environment (see the Inject example below).

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.6.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.6.0"

```

### [Advanced](modules/example/src/main/scala/io/janstenpickle/trace4cats/example/AdvancedExample.scala)

Demonstrates how spans may be used in a for comprehension along side other [`Resource`]s.
Also shows how multiple completers may be combined using a monoid in the
[`AllCompleters`](modules/example/src/main/scala/io/janstenpickle/trace4cats/example/AllCompleters.scala)
object.

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.6.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.6.0"
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

### [Inject](modules/example/src/main/scala/io/janstenpickle/trace4cats/example/InjectExample.scala)

Demonstrates how the callstack may be traced using the [`Trace`](modules/inject/src/main/scala/io/janstenpickle/trace4cats/inject/Trace.scala)
typeclass. This functionality has been slightly adapted from [Natchez], but gives
you the ability to set the span's kind on creation and status during execution.

It also shows how via the `io.janstenpickle.trace4cats.natchez.conversions._` import
you can implicitly convert to and from [Natchez]'s `Trace` typeclass so if
you have imported some library that makes use of [Natchez] you can
interoperate with Trace4Cats.

Requires:


```scala
"io.janstenpickle" %% "trace4cats-core" % "0.6.0"
"io.janstenpickle" %% "trace4cats-inject" % "0.6.0"
"io.janstenpickle" %% "trace4cats-natchez" % "0.6.0" // required only for interop
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.6.0"

```

### [Inject ZIO](modules/example/src/main/scala/io/janstenpickle/trace4cats/example/InjectZioExample.scala)

Demonstrates how to use the `Trace` typeclass with [ZIO] environment instead of a Kleisli. A `Trace` instance
is provided with the following import:

```
io.janstenpickle.trace4cats.inject.zio._
```

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.6.0"
"io.janstenpickle" %% "trace4cats-inject-zio" % "0.6.0"
"io.janstenpickle" %% "trace4cats-natchez" % "0.6.0" // required only for interop
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.6.0"

```

### [Natchez](modules/example/src/main/scala/io/janstenpickle/trace4cats/example/NatchezExample.scala)

Demonstrates how the callstack may be traced with Trace4Cats using the [Natchez] `Trace`
typeclass.

It also shows how via the `io.janstenpickle.trace4cats.natchez.conversions._` import
you can implicitly convert to and from Trace4Cats' `Trace` typeclass for
interopability.

Requires:


```scala
"io.janstenpickle" %% "trace4cats-core" % "0.6.0"
"io.janstenpickle" %% "trace4cats-inject" % "0.6.0" // required only for interop
"io.janstenpickle" %% "trace4cats-natchez" % "0.6.0" 
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.6.0"

```


### [FS2](modules/example/src/main/scala/io/janstenpickle/trace4cats/example/Fs2Example.scala)

Demonstrates how a span context can be propagated through an [FS2] stream. Uses the 
[Writer monad](http://eed3si9n.com/herding-cats/Writer.html) to include an [FS2 `EntryPoint`] along side each element. 
Implicit methods are provided with the import `io.janstenpickle.trace4cats.fs2.syntax.all._` to lift an 
[FS2 `EntryPoint`] into the stream and use it to perform traced operations within the stream, propagating a span context
between closures.  

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.6.0"
"io.janstenpickle" %% "trace4cats-fs2" % "0.6.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.6.0"

```

### [Http4s](modules/example/src/main/scala/io/janstenpickle/trace4cats/example/Http4sExample.scala)

Demonstrates how to use Trace4Cats with [Http4s] routes and clients. Span contexts are propagated via HTTP headers, and 
span status is derived from HTTP status codes. Implicit methods on `HttpRoutes` and `Client` are provided with the
imports:

```scala
io.janstenpickle.trace4cats.http4s.server.syntax._
io.janstenpickle.trace4cats.http4s.client.syntax._
```

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.6.0"
"io.janstenpickle" %% "trace4cats-inject" % "0.6.0"
"io.janstenpickle" %% "trace4cats-http4s-client" % "0.6.0"
"io.janstenpickle" %% "trace4cats-http4s-server" % "0.6.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.6.0"

```

### [Http4s ZIO](modules/example/src/main/scala/io/janstenpickle/trace4cats/example/Http4sZioExample.scala)

Demonstrates how to use Trace4Cats with [Http4s] routes and clients. Span contexts are propagated via HTTP headers, and 
span status is derived from HTTP status codes. Implicit methods on `HttpRoutes` and `Client` are provided with the
imports:

```scala
io.janstenpickle.trace4cats.http4s.server.syntax._
io.janstenpickle.trace4cats.http4s.client.syntax._
io.janstenpickle.trace4cats.inject.zio._
```

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.6.0"
"io.janstenpickle" %% "trace4cats-inject-zio" % "0.6.0"
"io.janstenpickle" %% "trace4cats-http4s-client" % "0.6.0"
"io.janstenpickle" %% "trace4cats-http4s-server" % "0.6.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.6.0"

```

### [Sttp](modules/example/src/main/scala/io/janstenpickle/trace4cats/example/SttpExample.scala)

Demonstrates how to use Trace4Cats with an [Sttp] backend. Span contexts are propagated via HTTP headers, and 
span status is derived from HTTP status codes.

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.6.0"
"io.janstenpickle" %% "trace4cats-inject" % "0.6.0"
"io.janstenpickle" %% "trace4cats-sttp-client" % "0.6.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.6.0"

```

### [Sttp ZIO](modules/example/src/main/scala/io/janstenpickle/trace4cats/example/SttpZioExample.scala)

Demonstrates how to use Trace4Cats with an [Sttp] backend and [ZIO]. Span contexts are propagated via HTTP headers, and 
span status is derived from HTTP status codes.

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.6.0"
"io.janstenpickle" %% "trace4cats-inject-zio" % "0.6.0"
"io.janstenpickle" %% "trace4cats-sttp-client" % "0.6.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.6.0"

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

## TODO

- [x] Initial release
- [x] Probabilistic span sampler 
- [ ] Limit number of attributes
- [x] Integration tests
- [x] Detailed examples
- [x] Jaeger protobuf exporter
- [x] OTLP HTTP exporter

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
[Kafka consumer config]: https://kafka.apache.org/26/javadoc/?org/apache/kafka/clients/consumer/ConsumerConfig.html
[Kafka producer config]: https://kafka.apache.org/26/javadoc/?org/apache/kafka/clients/producer/ProducerConfig.html
