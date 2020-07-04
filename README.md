# Trace4Cats

Yet another distributed tracing system, this time just for Scala. Heavily relies upon
[Cats](https://typelevel.org/cats) and [Cats-effect](https://typelevel.org/cats-effect).

Designed for use with [Natchez], compatible with 
[OpenTelemetry] and [Jaeger].

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
- [OpenTelemetry] collector via Protobufs over GRPC
- Log using [Log4Cats]
- Trace4Cats Avro over TCP or UDP
- [Stackdriver Trace] over HTTP or GRPC

#### `SpanSampler`
Used to decide whether or not a span should be sampled.

The following implementations are provided out of the box:

- Always
- Never
- ~Probabilistic~ (soon)

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
docker run -it janstenpickle/trace4cats-agent:<version or latest>
```

### Collector

A standalone server designed to forward spans via various `SpanExporter` implementations. Currently
the Collector supports the following exporters:

- [Jaeger] via Thrift over UDP and Protobufs over GRPC
- [OpenTelemetry] via Protobufs over GRPC
- Log using [Log4Cats]
- Trace4Cats Avro over TCP
- [Stackdriver Trace] over HTTP and GRPC

#### Running

```bash
docker run -it janstenpickle/trace4cats-collector:<version or latest>
```

### Collector Lite

Similar implementation to the Collector, but compiled with [`native-image`] so does not support any
GRPC based exporters. Currently Collector lite supports the following exporters:

- [Jaeger] via Thrift over UDP
- Log using [Log4Cats]
- Trace4Cats Avro over TCP
- [Stackdriver Trace] over HTTP

#### Running

```bash
docker run -it janstenpickle/trace4cats-collector-lite:<version or latest>
```

## Example Usage

To use Trace4Cats within your application add the dependencies listed below as needed:

```scala
"io.janstenpickle" %% "trace4cats-core" % <version>
"io.janstenpickle" %% "trace4cats-natchez" % <version>
"io.janstenpickle" %% "trace4cats-avro-exporter" % <version>
"io.janstenpickle" %% "trace4cats-jaeger-thrift-exporter" % <version>
"io.janstenpickle" %% "log-exporter" % <version>
"io.janstenpickle" %% "opentelemetry-otlp-exporter" % <version>
"io.janstenpickle" %% "opentelemetry-jaeger-exporter" % <version>
"io.janstenpickle" %% "stackdriver-grpc-exporter" % <version>
"io.janstenpickle" %% "stackdriver-http-exporter" % <version>

```

The example below shows how to create a [Natchez] trace `entryPoint` using Trace4Cats. Spans will
be sent to both Jaeger and the Trace4Cats agent via UDP. For an example on how to use [Natchez] see
the project's [example code in github](https://github.com/tpolecat/natchez/blob/master/modules/examples/src/main/scala/Example.scala).

```scala
import cats.effect._
import cats.syntax.monoid._
import io.janstenpickle.trace4cats.kernel._
import io.janstenpickle.trace4cats.model._
import io.janstenpickle.trace4cats.natchez.Trace4CatsTracer
import io.janstenpickle.trace4cats.avro.AvroSpanCompleter
import io.janstenpickle.trace4cats.jaeger.JaegerSpanCompleter

val process = TraceProcess("SomeServiceName")

val entryPoint: Resource[IO, Entrypoint[IO]] =
  for {
    blocker <- Blocker[IO]
    jaeger <- JaegerSpanCompleter[IO](blocker, process)
    avro <- AvroSpanCompleter.udp[IO](blocker, process)
  } yield Trace4CatsTracer.entryPoint[IO](SpanSampler.always[IO], jaeger |+| avro)
```

## [`native-image`] Compatibility

The following span completers have been found to be compatible with [`native-image`]:

- Trace4Cats Avro
- [Jaeger] Thrift over UDP
- Log
- [Stackdriver Trace] over HTTP

## TODO

- [ ] Initial release
- [ ] Probabilistic span sampler 
- [x] Integration tests
- [ ] Detailed examples
- [x] Jaeger protobuf exporter
- [ ] OTLP HTTP exporter


[Jaeger]: https://www.jaegertracing.io/
[Log4Cats]: https://github.com/ChristopherDavenport/log4cats
[Natchez]: https://github.com/tpolecat/natchez
[`native-image`]: https://www.graalvm.org/docs/reference-manual/native-image/ 
[OpenTelemetry]: http://opentelemetry.io
[Stackdriver Trace]: https://cloud.google.com/trace/docs/reference
