# Code Examples

 * [Simple](#simple)
 * [Simple ZIO](#simple-zio)
 * [Advanced](#advanced)
 * [Inject](#inject)
 * [Inject ZIO](#inject-zio)
 * [Natchez](#natchez)
 * [FS2](#fs2)
 * [FS2 Advanced](#fs2-advanced) 
 * [Http4s](#http4s)
 * [Http4s ZIO](#http4s-zio)
 * [Sttp](#http4s)
 * [Sttp ZIO](#http4s-zio)   

## [Simple](../modules/example/src/main/scala/io/janstenpickle/trace4cats/example/SimpleExample.scala)

This example shows a simple example of how to use Trace4Cats without the need to 
inject a root span via a Kleisli (see the Inject example below).

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.9.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.9.0"

```

## [Simple ZIO](../modules/example/src/main/scala/io/janstenpickle/trace4cats/example/SimpleZioExample.scala)

This example shows a simple example of how to use Trace4Cats with ZIO without the need to 
inject a root span via the environment (see the Inject example below).

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.9.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.9.0"

```

## [Advanced](0.9.0)

Demonstrates how spans may be used in a for comprehension along side other [`Resource`]s.
Also shows how multiple completers may be combined using a monoid in the
[`AllCompleters`](../modules/example/src/main/scala/io/janstenpickle/trace4cats/example/AllCompleters.scala)
object.

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.9.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.9.0"
"io.janstenpickle" %% "trace4cats-jaeger-thrift-exporter" % "0.9.0"
"io.janstenpickle" %% "trace4cats-log-exporter" % "0.9.0"
"io.janstenpickle" %% "trace4cats-opentelemetry-otlp-grpc-exporter" % "0.9.0"
"io.janstenpickle" %% "trace4cats-opentelemetry-otlp-http-exporter" % "0.9.0"
"io.janstenpickle" %% "trace4cats-opentelemetry-jaeger-exporter" % "0.9.0"
"io.janstenpickle" %% "trace4cats-stackdriver-grpc-exporter" % "0.9.0"
"io.janstenpickle" %% "trace4cats-stackdriver-http-exporter" % "0.9.0"
"io.janstenpickle" %% "trace4cats-datadog-http-exporter" % "0.9.0"
"io.janstenpickle" %% "trace4cats-newrelic-http-exporter" % "0.9.0"

```

### [Inject](../modules/example/src/main/scala/io/janstenpickle/trace4cats/example/InjectExample.scala)

Demonstrates how the callstack may be traced using the [`Trace`](../modules/inject/src/main/scala/io/janstenpickle/trace4cats/inject/Trace.scala)
typeclass. This functionality has been slightly adapted from [Natchez], but gives
you the ability to set the span's kind on creation and status during execution.

It also shows how via the `io.janstenpickle.trace4cats.natchez.conversions._` import
you can implicitly convert to and from [Natchez]'s `Trace` typeclass so if
you have imported some library that makes use of [Natchez] you can
interoperate with Trace4Cats.

Requires:


```scala
"io.janstenpickle" %% "trace4cats-core" % "0.9.0"
"io.janstenpickle" %% "trace4cats-inject" % "0.9.0"
"io.janstenpickle" %% "trace4cats-natchez" % "0.9.0" // required only for interop
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.9.0"

```

### [Inject ZIO](../modules/example/src/main/scala/io/janstenpickle/trace4cats/example/InjectZioExample.scala)

Demonstrates how to use the `Trace` typeclass with [ZIO] environment instead of a Kleisli. A `Trace` instance
is provided with the following import:

```
io.janstenpickle.trace4cats.inject.zio._
```

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.9.0"
"io.janstenpickle" %% "trace4cats-inject-zio" % "0.9.0"
"io.janstenpickle" %% "trace4cats-natchez" % "0.9.0" // required only for interop
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.9.0"

```

## [Natchez](../modules/example/src/main/scala/io/janstenpickle/trace4cats/example/NatchezExample.scala)

Demonstrates how the callstack may be traced with Trace4Cats using the [Natchez] `Trace`
typeclass.

It also shows how via the `io.janstenpickle.trace4cats.natchez.conversions._` import
you can implicitly convert to and from Trace4Cats' `Trace` typeclass for
interopability.

Requires:


```scala
"io.janstenpickle" %% "trace4cats-core" % "0.9.0"
"io.janstenpickle" %% "trace4cats-inject" % "0.9.0" // required only for interop
"io.janstenpickle" %% "trace4cats-natchez" % "0.9.0" 
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.9.0"

```


## [FS2](../modules/example/src/main/scala/io/janstenpickle/trace4cats/example/Fs2Example.scala)

Demonstrates how a span can be propagated through an [FS2] stream. Uses the 
[Writer monad](http://eed3si9n.com/herding-cats/Writer.html) to include an [`Span`] along side each element. 
Implicit methods are provided with the import `io.janstenpickle.trace4cats.fs2.syntax.all._` to lift an 
[`EntryPoint`] into the stream and use it to perform traced operations within the stream, propagating a span
between closures.  

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.9.0"
"io.janstenpickle" %% "trace4cats-fs2" % "0.9.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.9.0"

```

## [FS2 Advanced](../modules/example/src/main/scala/io/janstenpickle/trace4cats/example/Fs2AdvancedExample.scala)


Very similar to the [FS2 example above](#fs2), however the effect type on the stream is lifted to the traced effect type
allowing for traced operations with the same effect type to be performed within the stream. This avoids the need for
having to use two type parameters when passing around a `TracedStream`.

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.9.0"
"io.janstenpickle" %% "trace4cats-fs2" % "0.9.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.9.0"

```

## [Http4s](../modules/example/src/main/scala/io/janstenpickle/trace4cats/example/Http4sExample.scala)

Demonstrates how to use Trace4Cats with [Http4s] routes and clients. Span contexts are propagated via HTTP headers, and 
span status is derived from HTTP status codes. Implicit methods on `HttpRoutes` and `Client` are provided with the
imports:

```scala
io.janstenpickle.trace4cats.http4s.server.syntax._
io.janstenpickle.trace4cats.http4s.client.syntax._
```

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.9.0"
"io.janstenpickle" %% "trace4cats-inject" % "0.9.0"
"io.janstenpickle" %% "trace4cats-http4s-client" % "0.9.0"
"io.janstenpickle" %% "trace4cats-http4s-server" % "0.9.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.9.0"

```

## [Http4s ZIO](../modules/example/src/main/scala/io/janstenpickle/trace4cats/example/Http4sZioExample.scala)

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
"io.janstenpickle" %% "trace4cats-core" % "0.9.0"
"io.janstenpickle" %% "trace4cats-inject-zio" % "0.9.0"
"io.janstenpickle" %% "trace4cats-http4s-client" % "0.9.0"
"io.janstenpickle" %% "trace4cats-http4s-server" % "0.9.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.9.0"

```

## [Sttp](../modules/example/src/main/scala/io/janstenpickle/trace4cats/example/SttpExample.scala)

Demonstrates how to use Trace4Cats with an [Sttp] backend. Span contexts are propagated via HTTP headers, and 
span status is derived from HTTP status codes.

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.9.0"
"io.janstenpickle" %% "trace4cats-inject" % "0.9.0"
"io.janstenpickle" %% "trace4cats-sttp-client" % "0.9.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.9.0"

```

## [Sttp ZIO](../modules/example/src/main/scala/io/janstenpickle/trace4cats/example/SttpZioExample.scala)

Demonstrates how to use Trace4Cats with an [Sttp] backend and [ZIO]. Span contexts are propagated via HTTP headers, and 
span status is derived from HTTP status codes.

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.9.0"
"io.janstenpickle" %% "trace4cats-inject-zio" % "0.9.0"
"io.janstenpickle" %% "trace4cats-sttp-client" % "0.9.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.9.0"

```

## [Attribute Filtering](../modules/example/src/main/scala/io/janstenpickle/trace4cats/example/AttributeFiltering.scala)

Demonstrates how to use the [attribute filter](filtering.md) with an exporter in order to remove sensitive attributes 
from spans. Note that this would more commonly be configured on the [collector].

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.9.0"
"io.janstenpickle" %% "trace4cats-filtering" % "0.9.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.9.0"

```

## [Tail Sampling](../modules/example/src/main/scala/io/janstenpickle/trace4cats/example/TailSampling.scala)

Demonstrates how to use [tail samplers](sampling.md#tail-sampling) with an exporter in order to sample spans after they
have been created. Note that this would more commonly be configured on the [collector].

Requires:

```scala
"io.janstenpickle" %% "trace4cats-core" % "0.9.0"
"io.janstenpickle" %% "trace4cats-filtering" % "0.9.0"
"io.janstenpickle" %% "trace4cats-avro-exporter" % "0.9.0"

```

[FS2]: https://fs2.io/
[FS2 `EntryPoint`]: ../modules/fs2/src/main/scala/io/janstenpickle/trace4cats/fs2/Fs2EntryPoint.scala
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
[Kafka consumer config]: https://kafka.apache.org/26/javadoc/?org/apache/kafka/clients/consumer/ConsumerConfig.html
[Kafka producer config]: https://kafka.apache.org/26/javadoc/?org/apache/kafka/clients/producer/ProducerConfig.html
[collector]: components.md#collectors