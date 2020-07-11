## [0.2.0] - 2020-07-11

### Added

* Modules:
 - [`inject`]: Based on [Natchez] but exposes more Trace4Cats functionality
 - [`exporter-http`]: Common code for exporting over HTTP
 - [`datadog-http-exporter`]: Export to Datadog via HTTP
 - [`newrelic-http-exporter`]: Export to NewRelic via HTTP

* Housekeeping
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


[0.2.0]: https://github.com/janstenpickle/trace4cats/compare/v0.1.0..v0.2.0
[0.1.0]: https://github.com/janstenpickle/trace4cats/tree/v0.1.0
