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

#### `SpanCompleter`
Used when a span or batch of spans are finished. Multiple implementations may be combined using
the provided `Monoid` instance.

The following implementations are provided out of the box:

- [Jaeger] agent via Thrift over UDP
- [OpenTelemetry] collector via Protobufs over GRPC
- Log using [Log4Cats]
- Trace4Cats Avro over TCP or UDP

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

A standalone server designed to forward spans via various `SpanCompleter` implementations. Currently
the collector supports the following forwarders:

- [Jaeger] via Thrift over UDP
- [OpenTelemetry] via Protobufs over GRPC
- Log using [Log4Cats]
- Trace4Cats Avro over TCP

#### Running

```bash
docker run -it janstenpickle/trace4cats-collector:<version or latest>
```

## Example Usage

To use Trace4Cats within your application add the dependencies listed below as needed:

```scala
"io.janstenpickle" %% "trace4cats-core" % <version>
"io.janstenpickle" %% "trace4cats-natchez" % <version>
"io.janstenpickle" %% "trace4cats-avro-completer" % <version>
"io.janstenpickle" %% "trace4cats-jaeger-thrift-completer" % <version>
"io.janstenpickle" %% "log-completer" % <version>
"io.janstenpickle" %% "opentelemetry-completer" % <version>
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
- Jaeger Thrift over UDP
- Log

[Jaeger]: https://www.jaegertracing.io/
[Log4Cats]: https://github.com/ChristopherDavenport/log4cats
[Natchez]: https://github.com/tpolecat/natchez
[`native-image`]: https://www.graalvm.org/docs/reference-manual/native-image/ 
[OpenTelemetry]: http://opentelemetry.io

## TODO

- [ ] Initial release
- [ ] Integration tests
- [ ] Detailed examples
- [ ] Jaeger protobuf forwarder