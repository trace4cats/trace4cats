## [0.5.1] - 2020-10-21

### Fixed

* [`stackdriver-grpc-exporter`]
  - Fix match error on repeated attribute values

## [0.5.0] - 2020-10-20

### Added

* [`inject-zio`], [`http4s-server-zio`],  [`http4s-client-zio`]
  - Add [ZIO] support as a substitute for a Kleisli monad transformer ([#72](../../pull/72)
  

### Changed

* [`model`]
  - Allow list based attribute values ([#76](../../pull/76))
  
* [`opentelemetry-otlp-grpc-exporter`]
  - Fix channel resource leak in OT GRPC span exporter by [@catostrophe] ([#63](../../pull/63))

* [`http4s-server`] and  [`http4s-client`]
  - Abstract away server and client tracers ([#72](../../pull/72)
  
* [`example`]
  - Update examples to show how to use [ZIO] as an effect type ([#72](../../pull/72)

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
  `0.8.0`
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
[`inject`]: modules/inject
[`inject-zio`]: modules/inject-zio
[`fs2`]: modules/fs2
[`example`]: modules/example
[`http4s-common`]: modules/http4s-common
[`http4s-client`]: modules/http4s-client
[`http4s-server`]: modules/http4s-server
[`http4s-client-zio`]: modules/http4s-client-zio
[`http4s-server-zio`]: modules/http4s-server-zio
[`test`]: modules/avro
[`avro`]: modules/avro
[`avro-exporter`]: modules/avro-exporter
[`avro-server`]: modules/avro-exporter
[`exporter-common`]: modules/exporter-common
[`exporter-http`]: modules/exporter-http
[`jaeger-thrift-exporter`]: modules/jaeger-thrift-exporter
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
[`collector`]: modules/collector
[`collector-lite`]: modules/collector-lite

[Natchez]: https://github.com/tpolecat/natchez
[FS2]: https://fs2.io
[Http4s]: https://http4s.org
[ZIO]: https://zio.dev

[0.5.0]: https://github.com/janstenpickle/trace4cats/compare/v0.4.0..v0.5.0
[0.4.0]: https://github.com/janstenpickle/trace4cats/compare/v0.3.0..v0.4.0
[0.3.0]: https://github.com/janstenpickle/trace4cats/compare/v0.2.0..v0.3.0
[0.2.0]: https://github.com/janstenpickle/trace4cats/compare/v0.1.0..v0.2.0
[0.1.0]: https://github.com/janstenpickle/trace4cats/tree/v0.1.0

[@catostrophe]: https://github.com/catostrophe