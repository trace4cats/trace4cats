# Design

Trace 4 Cats partially implements [OpenTelemetry] tracing, just enough that traces can be exported to [Jaeger] or the 
[OpenTelemetry collector](https://opentelemetry.io/docs/collector/about/). 

## Trace Injection

Based heavily on [Natchez] but exposes the specific Trace4Cats functionality of setting the span
kind and status. A `Trace` typeclass is used to propagate a span context throughout the callstack.

See the [example](examples.md#inject) below for more information, usage, and interoperability with [Natchez].

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
Used to decide whether or not a span should be sampled. For more information on sampling see the 
[associated documentation](sampling.md)

The following implementations are provided out of the box:

- Always
- Never
- Probabilistic
- Rate

#### `AttributeValue`

An ADT for representing different types of span attributes, currently supports the following types as single values or
as lists.

- String
- Boolean
- Double
- Long

Unlike many other tracing libraries, `AttributeValue`s are lazily evaluated, so if a span is not sampled the value will
not be computed. This is especially useful when a value requires some computation before it can be displayed, such as
the size of a collection.

#### `ToHeaders`

Convert a span context to and from message or http headers.

The following implementations are provided out of the box:

- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
- [B3](https://github.com/openzipkin/b3-propagation)
- [B3 Single Header](https://github.com/openzipkin/b3-propagation)
- [Envoy](https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/observability/tracing)

When using `ToHeaders.all` the span context will be encoded using all of these header types and decoded using each
encoding as a fallback, allowing for maximum interoperability between tracing systems.


[FS2]: https://fs2.io/
[FS2 `EntryPoint`]: modules/fs2/src/main/scala/io/janstenpickle/trace4cats/fs2/Fs2EntryPoint.scala
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
