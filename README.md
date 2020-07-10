# Trace4Cats

Yet another distributed tracing system, this time just for Scala. Heavily relies upon
[Cats](https://typelevel.org/cats) and [Cats-effect](https://typelevel.org/cats-effect).

Compatible with [OpenTelemetry] and [Jaeger], based on, and interoperates wht [Natchez].

[Obligatory XKCD](https://xkcd.com/927/)

## Motivation

It increasingly seems that Java tracing libraries are dependent on GRPC, which usually
brings along lots of other dependencies. You may find *Trace4Cats* useful if you want to...

- Reduce the number of dependencies in your application
- Resolve a dependency conflict caused by a tracing implementation
- Create a [`native-image`] using [Graalvm](https://www.graalvm.org/)
  
## Design

Trace 4 Cats partially implements [OpenTelemetry] tracing, just enough
traces can be exported to [Jaeger] or the 
[OpenTelemetry collector](https://opentelemetry.io/docs/collector/about/). It is designed for use
with [Natchez], but may also be used on its own.

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
- Trace4Cats Avro over TCP or UDP
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
docker run -it janstenpickle/trace4cats-agent:0.1.0
```

### Collector

A standalone server designed to forward spans via various `SpanExporter` implementations. Currently
the Collector supports the following exporters:

- [Jaeger] via Thrift over UDP and Protobufs over GRPC
- [OpenTelemetry] via Protobufs over GRPC and JSON over HTTP
- Log using [Log4Cats]
- Trace4Cats Avro over TCP
- [Stackdriver Trace] over HTTP and GRPC
- [Datadog] over HTTP
- [NewRelic] over HTTP

#### Running

```bash
docker run -p7777:7777/udp -it janstenpickle/trace4cats-collector:0.1.0
```

### Collector Lite

Similar implementation to the Collector, but compiled with [`native-image`] so does not support any
GRPC based exporters. Currently Collector lite supports the following exporters:

- [Jaeger] via Thrift over UDP
- [OpenTelemetry] via JSON over HTTP
- Log using [Log4Cats]
- Trace4Cats Avro over TCP
- [Stackdriver Trace] over HTTP
- [Datadog] over HTTP
- [NewRelic] over HTTP

#### Running

```bash
docker run -p7777:7777 -p7777:7777/udp -it janstenpickle/trace4cats-collector-lite:0.1.0
```

## SBT Dependencies

To use Trace4Cats within your application add the dependencies listed below as needed:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.1.0"
"io.janstenpickle" %% "trace4cats-inject" % "0.1.0"
"io.janstenpickle" %% "trace4cats-natchez" % "0.1.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-jaeger-thrift-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-log-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-opentelemetry-otlp-grpc-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-opentelemetry-otlp-http-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-opentelemetry-jaeger-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-stackdriver-grpc-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-stackdriver-http-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-datadog-http-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-newrelic-http-exporter" % "0.1.0"

```

## Code Examples

### [Simple](../../tree/master/modules/example/src/main/scala/io/janstenpickle/trace4cats/example/SimpleExample.scala)

This example shows a simple example of how to use Trace4Cats without the need to 
inject a root span via a Kleisli (see the Inject example below).

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.1.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.1.0"

```

### [Advanced](../../tree/master/modules/example/src/main/scala/io/janstenpickle/trace4cats/example/AdvancedExample.scala)

Demonstrates how spans may be used in a for comprehension along side other [`Resource`]s.
Also shows how multiple completers may be combined using a monoid in the
[`AllCompleters`](../../tree/master/modules/example/src/main/scala/io/janstenpickle/trace4cats/example/AllCompleters.scala)
object.

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.1.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-jaeger-thrift-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-log-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-opentelemetry-otlp-grpc-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-opentelemetry-otlp-http-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-opentelemetry-jaeger-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-stackdriver-grpc-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-stackdriver-http-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-datadog-http-exporter" % "0.1.0"
"io.janstenpickle" %% "trace4cats-newrelic-http-exporter" % "0.1.0"

```

### [Inject](../../tree/master/modules/example/src/main/scala/io/janstenpickle/trace4cats/example/InjectExample.scala)

Demonstrates how the callstack may be traced using the [`Trace`](../../tree/master/modules/inject/src/main/scala/io/janstenpickle/trace4cats/inject/Trace.scala)
typeclass. This functionality has been slightly adapted from [Natchez], but gives
you the ability to set the span's kind on creation and status during execution.

It also shows how via the `io.janstenpickle.trace4cats.natchez.conversions._` import
you can implicitly convert to and from [Natchez]'s `Trace` typeclass so if
you have imported some library that makes use of [Natchez] you can
interoperate with Trace4Cats.

Requires:


```scala
"io.janstenpickle" %% "trace4cats-core" % "0.1.0"
"io.janstenpickle" %% "trace4cats-inject" % "0.1.0"
"io.janstenpickle" %% "trace4cats-natchez" % "0.1.0" // required only for interop
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.1.0"

```

### [Natchez](../../tree/master/modules/example/src/main/scala/io/janstenpickle/trace4cats/example/NatchezExample.scala)

Demonstrates how the callstack may be traced with Trace4Cats using the [Natchez] `Trace`
typeclass.

It also shows how via the `io.janstenpickle.trace4cats.natchez.conversions._` import
you can implicitly convert to and from Trace4Cats' `Trace` typeclass for
interopability.

Requires:


```scala
"io.janstenpickle" %% "trace4cats-core" % "0.1.0"
"io.janstenpickle" %% "trace4cats-inject" % "0.1.0" // required only for interop
"io.janstenpickle" %% "trace4cats-natchez" % "0.1.0" 
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.1.0"

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

## TODO

- [x] Initial release
- [x] Probabilistic span sampler 
- [ ] Limit number of attributes
- [x] Integration tests
- [x] Detailed examples
- [x] Jaeger protobuf exporter
- [x] OTLP HTTP exporter


[Jaeger]: https://www.jaegertracing.io/
[Log4Cats]: https://github.com/ChristopherDavenport/log4cats
[Natchez]: https://github.com/tpolecat/natchez
[`native-image`]: https://www.graalvm.org/docs/reference-manual/native-image/ 
[OpenTelemetry]: http://opentelemetry.io
[Stackdriver Trace]: https://cloud.google.com/trace/docs/reference
[Datadog]: https://docs.datadoghq.com/api/v1/tracing/
[NewRelic]: https://docs.newrelic.com/docs/understand-dependencies/distributed-tracing/trace-api/report-new-relic-format-traces-trace-api#new-relic-guidelines 
[`Resource`]: https://typelevel.org/cats-effect/datatypes/resource.html
