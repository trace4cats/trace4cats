## [0.9.0] - 2021-01-24

Another substantial release, with a much improved typeclass hierarchy for generalising trace and request context
injection.

A big thank you to [@catostrophe] who as done most of the feature work for this release, especially with the new
typclass hierarchy in [`base`].

### Added

* [`base`], [`base-laws`] and [`base-zio`]
  - Typeclass hierarchy and basic optics to support trace/general context injection by [@catostrophe] ([#129](../../issues/129))
  - Laws for the typeclasses defined in [`base`] by [@catostrophe] ([#129](../../issues/129))
  - [ZIO] typeclass instances instances by [@catostrophe] ([#129](../../issues/129))

* [`sttp-client3`]
  - Syntax for lifting an [Sttp] version 3 client into a trace context by [@catostrophe] ([#128](../../issues/128))

* [`sttp-tapir`]
  - Syntax for lifting a [Tapir] server into a trace context by [@catostrophe] ([#128](../../issues/128))

* [`docker-compose.yml`]
  - Docker compose definition for use with tests ([#146](../../issues/146))
  
### Changed

* [`model`]
  - Add newtype for headers map by [@catostophe] ([#124](../../issues/124))
  - Fix span reference mapping [@catostophe] ([#151](../../issues/151))
 
* [`jaeger-thrift-exporter`]
  - Fix span reference mapping [@catostophe] ([#151](../../issues/151))

* [`opentelemetry-jaeger-exporter`]
  - Fix span reference mapping [@catostophe] ([#151](../../issues/151))
  
* [`opentelemetry-otlp-grpc-exporter`]
  - Fix span reference mapping [@catostophe] ([#151](../../issues/151))

* [`opentelemetry-otlp-http-exporter`]
  - Fix span reference mapping [@catostophe] ([#151](../../issues/151))

* [`stackdriver-grpc-exporter`]
  - Fix span reference mapping [@catostophe] ([#151](../../issues/151))

* [`stackdriver-http-exporter`]
  - Fix span reference mapping [@catostophe] ([#151](../../issues/151))
  
* [`http4s-client`]
  - Remove http4s server in tests ([#184](../../pull/184))
  
* [`http4s-server`]
  - Remove http4s server in tests ([#184](../../pull/184))
  
## Housekeeping

[See a full list of updated libraries here.](../../pulls?q=is%3Apr+sort%3Aupdated-desc+is%3Amerged+updated%3A>2020-11-19++updated%3A<%3D2021-01-24+)
  
## [0.7.0] - 2020-11-09

The biggest release since [0.1.0], most notably this includes:

 - More detailed [documentation](README.md#documentation)
 - Support for sending spans via [Kafka]
 - Span context propagation via [Kafka] message headers
 - [Tail sampling](docs/sampling.md#tail-sampling) in the [Collector](docs/components.md#collectors)
 - [Rate based samplers](docs/sampling.md#rate)
 
**⚠️ Please note that this release is not backward compatible with any previous versions ⚠️**

### Added

* [`kafka-client`]
  - Support for trace context propagation via [Kafka] message headers ([#89](../../pull/89))
* [`graal-kafka`]
  - Class replacements which allow the [Kafka] client to work with Graal native-image ([#89](../../pull/89))
* [`avro-kafka-exporter`], [`avro-kafka-server`] and [`agent-kafka`]
  - Support for sending avro encoded spans via [Kafka] ([#89](../../pull/89))
* [`tail-sampling`], [`tail-sampling-cache-store`] and [`tail-sampling-redis-store`]
  - Tail sampling implementation for use in the collector ([#89](../../pull/89))
  - Add redis span decision store ([#103](../../pull/103))
* [`filtering`]
  - Add ability to filter out sensitive span attributes within the collector ([#103](../../pull/103))
* [`rate-sampling`]
  - Token bucket based rate sampler ([#107](../../pull/107))

### Changed

* [`core`]
  - Add support for more span context propagation header types ([#85](../../pull/85))
* [`http4s-common`] and [`http4s-server`]
  - Add support for excluding certain requests from starting traces ([#86](../../pull/86))
* [`opentelemetry-otlp-http-exporter`]
  - Add the ability to load a service account token from the instance metadata service in GCP ([#84](../../pull/84))
* [`collector`] and [`collector-lite`]
  - Configure collectors using YAML or JSON ([#89](../../pull/89))
  - Allow multiple instances of exporters to be configured ([#109](../../pull/109))
* [`model`], [`kernel`] and [`core`]
  - Change `Batch` to take a collection type param in order to cut down on transformations and traversals
    ([#103](../../pull/103))
  - Remove `TraceProcess` from `Batch` so that it may be flattened into `CompletedSpans`s and made into larger or
    smaller batches within a stream or exporter ([#103](../../pull/103))
  
### Removed

* [`http4s-server-zio`] and  [`http4s-client-zio`]
  - No longer required thanks to new typeclasses introduced as part of ([#89](../../pull/89))

### Housekeeping

  - `google-cloud-trace` to `1.2.6`
  - `kittens` to `2.2.0`
  - `sbt-bintray` to `0.6.1`
  - `grpc-api` and `grpc-okhttp` to `1.33.1`
  - `sbt` to `1.4.2`
  - `discipline-core` to `1.1.1`
  - `micronaut-core` to `2.1.3`
  - `scalacheck` to `1.15.1`
  - `scalafmt-core` to `2.7.5`
  - `scalatest` to `3.2.3`

## [0.6.0] - 2020-10-21

### Added

* [`sttp-client`],  [`sttp-client-zio`]
  - Add [Sttp] client support ([#82](../../pull/82))
 
## [0.5.2] - 2020-10-21

### Fixed

* [`stackdriver-grpc-exporter`]
  - Ensure service name is correctly converted to an attribute value
  
### Housekeeping

  - `grpc-api` and `grpc-okhttp` to `1.33.0`
  
## [0.5.1] - 2020-10-21

### Fixed

* [`stackdriver-grpc-exporter`]
  - Fix match error on repeated attribute values

## [0.5.0] - 2020-10-20

### Added

* [`inject-zio`], [`http4s-server-zio`],  [`http4s-client-zio`]
  - Add [ZIO] support as a substitute for a Kleisli monad transformer ([#72](../../pull/72))
  

### Changed

* [`model`]
  - Allow list based attribute values ([#76](../../pull/76))
  
* [`opentelemetry-otlp-grpc-exporter`]
  - Fix channel resource leak in OT GRPC span exporter by [@catostrophe] ([#63](../../pull/63))

* [`http4s-server`] and  [`http4s-client`]
  - Abstract away server and client tracers ([#72](../../pull/72))
  
* [`example`]
  - Update examples to show how to use [ZIO] as an effect type ([#72](../../pull/72))

### Housekeeping

  - `jwt` to `3.11.0`
  - `google-auth-library-credentials` to `0.22.0`
  - `google-cloud-trace` to `1.2.4`
  - `grpc-api` and `grpc-okhttp` to `1.32.2`
  - `http4s-core`, `http4s-dsl`, `http4-server`, `http4s-client`, `http4s-circe`, `http4s-blaze-client` 
  and `http4s-blaze-server` to `0.21.8`  
  - `natchez` to `0.0.13`
  - `opentelemetry-sdk`, `opentelemetry-proto`, `opentelemetry-exporters-otlp` and `opentelemetry-exporters-jaeger` to
  `0.9.1`  
  - `sbt` to `1.4.1`
  - `sbt-native-packager` to `1.7.6`
  - `sbt-tpolecat` to `0.1.14`
  - `scala2.12` to `2.12.12`

## [0.4.0] - 2020-09-13

### Added

* [`core`]
  - Allow specific exceptions to be captured and mapped to a `SpanStatus` on span completion ([#49](../../pull/49))

### Changed

* [`model`]
  - Override `toString` in `TraceId` and `SpanId` by [@catostrophe] ([#27](../../pull/27))
  
* [`core`]
  - Fix W3C header encoding ([#60](../../pull/60))

* [`http4s-server`],  [`http4s-client`] and [`http4s-common`]
  - Drop sensitive headers before putting them into the span by [@catostrophe] ([#29](../../pull/29))
  - Make span operation name configurable by [@catostrophe] ([#30](../../pull/30))
  - Update client and server syntax to address completion issues ([#49](../../pull/49))
  
* [`example`]
  - Fix errors in HTTP example by [@catostrophe] ([#28](../../pull/28))

### Housekeeping

  - `cats-core` and `cats-laws` to `2.2.0`
  - `cats-effect` and `cats-effect-laws` to `2.2.0`
  - `collection-compat` to `2.2.0`
  - `commons-codec` to `2.15`
  - `decline` to `1.3.0`
  - `fs2-core` and `fs2-io` to `2.4.4`
  - `google-cloud-trace` to `1.2.0`
  - `grpc-api` and `grpc-okhttp` to `1.32.1`
  - `http4s-core`, `http4s-dsl`, `http4-server`, `http4s-client`, `http4s-circe`, `http4s-blaze-client` 
  and `http4s-blaze-server` to `0.21.7`
  - `http4s-jdk-http-client` to `0.3.1`
  - `jaeger-thrift` to `1.4.0`
  - `natchez-core` to `0.0.12`
  - `opentelemetry-sdk`, `opentelemetry-proto`, `opentelemetry-exporters-otlp` and `opentelemetry-exporters-jaeger` to
  `0.9.0`
  - `vulcan-core`, `vulvan-generic` and `vulcan-enumeratum` to `1.2.0`
  - `discipline-scalatest` to `2.0.1`
  - `discipline-core` to `1.0.3`
  - `scalatest` to `3.2.2`
  
## [0.3.0] - 2020-07-18

### Changed

* [`model`]
  - Add invalid `TraceId`, `SpanId` and `SpanContext`

* [`core`]
  - Add `mapK` to `Span` by [@catostrophe] ([#16](../../pull/16))
  - Add noop `Span`
  - Remove private methods from `Span` - no longer needed
  
* [`inject`]
  - Add noop `EntryPoint` by [@catostrophe] ([#18](../../pull/18))
  - Add `EitherT` trace instance
  - Disambiguate `put` and `putAll` methods on `Trace`

### Added

* Modules:
  - [`fs2`]: Support for propagating span context through an [FS2] stream ([#19](../../pull/19))
  - [`http4s-common`]: Common http4s trace utilities ([#20](../../pull/20))
  - [`http4s-client`]: Tracing support for [Http4s] clients ([#20](../../pull/20))
  - [`http4s-server`]: Tracing support for [Http4s] routes ([#20](../../pull/20))

### Housekeeping
  - `cats-effect` and `cats-effect-laws` to `2.1.4`
  
## [0.2.0] - 2020-07-11

### Added

* Modules:
  - [`inject`]: Based on [Natchez] but exposes more Trace4Cats functionality
  - [`exporter-http`]: Common code for exporting over HTTP
  - [`datadog-http-exporter`]: Export to Datadog via HTTP
  - [`newrelic-http-exporter`]: Export to NewRelic via HTTP

### Housekeeping
  - `sbt-scalafmt` to `1.16`
  - `sbt-protoc` to `0.99.34`
  - `compilerplugin` to `0.10.7`
  - `jaeger-thrift` to `1.3.1`
  - `sbt` to `1.3.13`
  - `cats-effect` and `cats-effect-laws` to `2.1.3`
  - `google-auth-library-credentials` to `0.21.1`
  - `sbt-native-packager` to `1.7.4`

## [0.1.0] - 2020-07-05

Initial Release

### Added

* Modules:
  - [`model`]: Trace4Cats model
  - [`kernel`]: Trace4Cats kernel -depends on cats
  - [`core`]: Trace4Cats core - depends on cats-effect
  - [`test`]: Test utils for Trace4Cats
  - [`avro`]: Vulkan codecs for Trace4Cats [`model`]
  - [`avro-exporter`]: Avro exporter implementation for TCP/UDP
  - [`avro-server`]: Avro server that can receive in TCP/UDP
  - [`exporter-common`]: Common exporter utils
  - [`jaeger-thrift-exporter`]: Jaeger compact Thrift exporter
  - [`log-exporter`]: Log exporter
  - [`opentelemetry-jaeger-exporter`]: Export to Jaeger via GRPC
  - [`opentelemetry-otlp-grpc-exporter`]: Export to Open Telemetry collector via GRPC
  - [`opentelemetry-otlp-http-exporter`]: Export to Open Telemetry collector via HTTP
  - [`stackdriver-grpc-exporter`]: Export to Google Stackdriver via GRPC
  - [`stackdriver-http-exporter`]: Export to Google Stackdriver via HTTP
  - [`natchez`]: Interoperate with [Natchez]
  - [`agent`]: Trace4Cats Agent - built with native-image
  - [`collector`]: Trace4Cats Collector - forwards onto other implementations via exporters
  - [`collector-lite`]: Trace4Cats Collector Lite - native-image compatible exporters only

[`model`]: modules/model
[`kernel`]: modules/kernel
[`core`]: modules/core
[`base`]: modules/base
[`base-laws`]: modules/base-laws
[`base-zio`]: modules/base-zio
[`inject`]: modules/inject
[`inject-zio`]: modules/inject-zio
[`fs2`]: modules/fs2
[`example`]: modules/example
[`http4s-common`]: modules/http4s-common
[`http4s-client`]: modules/http4s-client
[`http4s-server`]: modules/http4s-server
[`http4s-client-zio`]: modules/http4s-client-zio
[`http4s-server-zio`]: modules/http4s-server-zio
[`sttp-client`]: modules/sttp-client
[`sttp-client3`]: modules/sttp-client3
[`sttp-tapir`]: modules/sttp-tapir
[`sttp-client-zio`]: modules/sttp-client-zio
[`test`]: modules/avro
[`avro`]: modules/avro
[`avro-exporter`]: modules/avro-exporter
[`avro-kafka-exporter`]: modules/avro-kafka-exporter
[`avro-server`]: modules/avro-exporter
[`avro-kafka-server`]: modules/avro-kafka-exporter
[`exporter-common`]: modules/exporter-common
[`exporter-http`]: modules/exporter-http
[`jaeger-thrift-exporter`]: modules/jaeger-thrift-exporter
[`kafka-client`]: modules/kafka-client
[`graal-kafka`]: modules/kafka-client
[`log-exporter`]: modules/log-exporter
[`opentelemetry-jaeger-exporter`]: modules/opentelemetry-jaeger-export
[`opentelemetry-otlp-grpc-exporter`]: modules/opentelemetry-otlp-grpc-exporter
[`opentelemetry-otlp-http-exporter`]: modules/opentelemetry-otlp-http-exporter
[`stackdriver-grpc-exporter`]: modules/stackdriver-grpc-exporter
[`stackdriver-http-exporter`]: modules/stackdriver-http-exporter
[`datadog-http-exporter`]: modules/datadog-http-exporter
[`newrelic-http-exporter`]: modules/newrelic-http-exporter
[`natchez`]: modules/natchez
[`agent`]: modules/agent
[`agent-kafka`]: modules/agent-kafka
[`collector`]: modules/collector
[`collector-lite`]: modules/collector-lite
[`filtering`]: modules/filtering
[`tail-sampling`]: modules/tail-sampling
[`tail-sampling-cache-store`]: modules/tail-sampling-cache-store
[`tail-sampling-redis-store`]: modules/tail-sampling-redis-store
[`rate-sampling`]: modules/rate-sampling
[`docker-compose.yml`]: docker-compose.yaml

[Natchez]: https://github.com/tpolecat/natchez
[FS2]: https://fs2.io
[Http4s]: https://http4s.org
[ZIO]: https://zio.dev
[Sttp]: https://sttp.softwaremill.com
[Tapir]: https://github.com/softwaremill/tapir
[Kafka]: https://kafka.apache.org

[0.9.0]: https://github.com/janstenpickle/trace4cats/compare/v0.7.0..v0.9.0
[0.7.0]: https://github.com/janstenpickle/trace4cats/compare/v0.6.0..v0.7.0
[0.6.0]: https://github.com/janstenpickle/trace4cats/compare/v0.5.2..v0.6.0
[0.5.2]: https://github.com/janstenpickle/trace4cats/compare/v0.5.1..v0.5.2
[0.5.1]: https://github.com/janstenpickle/trace4cats/compare/v0.5.0..v0.5.1
[0.5.0]: https://github.com/janstenpickle/trace4cats/compare/v0.4.0..v0.5.0
[0.4.0]: https://github.com/janstenpickle/trace4cats/compare/v0.3.0..v0.4.0
[0.3.0]: https://github.com/janstenpickle/trace4cats/compare/v0.2.0..v0.3.0
[0.2.0]: https://github.com/janstenpickle/trace4cats/compare/v0.1.0..v0.2.0
[0.1.0]: https://github.com/janstenpickle/trace4cats/tree/v0.1.0

[@catostrophe]: https://github.com/catostrophe