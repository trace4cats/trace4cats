# Components

  * [Agents](#agents)
    + [Agent](#agent)
      - [Running](#running)
    + [Agent Kafka](#agent-kafka)
      - [Running](#running-1)
  * [Collectors](#collectors)
    + [Collector](#collector)
      - [Configuring](#configuring)
      - [Running](#running-2)
    + [Collector Lite](#collector-lite)
      - [Configuring](#configuring-1)
      - [Running](#running-3)

The source code for these components is located in the
[`trace4cats-components`](https://github.com/trace4cats/trace4cats-components) repository.

## Agents

Agents are designed to be co-located with you app, see the [documentation on deployment topologies](topologies.md) for
information on how to use agents in different deployments.

### Agent

A lightweight Avro UDP server, built with [`native-image`] designed to be co-located with a traced
application. Forwards batches of spans onto the Collector over TCP.

#### Running

To see the command line options:

```bash
docker run -it janstenpickle/trace4cats-agent:0.12.0-RC1
```

Run in background:

```bash
docker run -p7777:7777/udp -d --rm \
  --name trace4cats-agent \
  janstenpickle/janstenpickle/trace4cats-agent:0.12.0-RC1
```

### Agent Kafka

A lightweight Avro UDP server, built with [`native-image`] designed to be co-located with a traced
application. Forwards batches of spans onto a supplied Kafka topic.

#### Running

To see the command line options:

```bash
docker run -it janstenpickle/trace4cats-agent-kafka:0.12.0-RC1
```

Run in background:

```bash
docker run -p7777:7777/udp -d --rm \
  --name trace4cats-agent \
  janstenpickle/janstenpickle/trace4cats-agent-kafka:0.12.0-RC1
```

## Collectors

Collectors are designed as standalone components that route spans to different systems, see the
[documentation on deployment topologies so](topologies.md) for information on how to use agents in different
deployments.


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

See Kafka documentation for additional [Kafka consumer] and [Kafka producer] config

```yaml
listener:
  port: 7779 # Change the default lister port

log-spans: true # Log spans to the console, defaults to false

buffer-size: 1000 # How many batches to buffer in case of a slow exporter, defaults to 500

# Span sampling
sampling:
  sample-probability: 0.05 # Optional - must be between 0 and 1.0. 1.0 being always sample, and 0.0 being never
  drop-span-names: # Optional - name of spans to drop (may be partial match)
    - some-span-name
  rate: # Optional - rate sampling
    max-batch-size: 1000
    token-rate-millis: 10
  cache-ttl-minutes: 10 # Cache duration for sample decision, defaults to 2 mins
  max-cache-size: 500000 # Max number of entries in the sample decision cache, defaults to 1000000
  redis: # Optional - use redis as a sample decision store
    host: redis-host
    port: 6378 # defaults to 6379

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
forwarders:
  - host: some-remote-host
    port: 7777

# Forward spans to a kafka topic
kafka-forwarders:
  - topic: spans
    bootstrap-servers:
      - "localhost:9092"
    # Optional additional Kafka producer config
    producer-config:
      key: value

# Export to Jaeger
jaeger:
  - host: jaeger-host
    port: 9999 # Defaults to 6831

# Export to Jaeger via protbufs
jaeger-proto:
  - host: jaeger-host
    port: 9999 # Defaults to 14250

# Export to OpenTelemetry Collector via HTTP
otlp-http:
  - host: otlp-host
    port: 9999 # Defaults to 55681

# Export to OpenTelemetry Collector via GRPC
otlp-grpc:
  - host: otlp-host
    port: 9999 # Defaults to 55680

# Export to Stackdriver via HTTP
# All config is optional, if running in GCP they will be obtained from the metadata endpoint
stackdriver-http:
  - project-id: some-project-id
    credentials-file: /path/to/credentials.json
    service-account-name: svcacc2 # Defaults to 'default'

# Export to Stackdriver via GRPC
stackdriver-grpc:
  - project-id: some-project-id

# Export to Datadog agent - All config is optional
datadog:
  - host: agent-host # defaults to 'localhost'
    port: 9999 # defaults to 8126

# Export to NewRelic
new-relic:
  - api-key: 7c1989d1-e019-46bc-a04e-824fdf33b237
    endpoint: EU # defaults to US, may be a custom endpoint
```

#### Running

```bash
docker run -p7777:7777 -p7777:7777/udp -d --rm \
  --name trace4cats-collector \
  -v /path/to/your/collector-config.yaml:/tmp/collector.yaml \
  janstenpickle/trace4cats-collector:0.12.0-RC1 --config-file=/tmp/collector.yaml
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
  sample-probability: 0.05 # Optional - must be between 0 and 1.0. 1.0 being always sample, and 0.0 being never
  drop-span-names: # Optional - name of spans to drop (may be partial match)
    - some-span-name
  rate: # Optional - rate sampling
    max-batch-size: 1000
    token-rate-millis: 10
  cache-ttl-minutes: 10 # Cache duration for sample decision, defaults to 2 mins
  max-cache-size: 500000 # Max number of entries in the sample decision cache, defaults to 1000000
  redis: # Optional - use redis as a sample decision store
    host: redis-host
    port: 6378 # defaults to 6379

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
  - host: some-remote-host
    port: 7777

# Forward spans to a kafka topic
kafka-forwarder:
  - topic: spans
    bootstrap-servers:
     - "localhost:9092"
    # Optional additional Kafka producer config
    producer-config:
      key: value

# Export to Jaeger
jaeger:
  - host: jaeger-host
    port: 9999 # Defaults to 6831

# Export to OpenTelemetry Collector via HTTP
otlp-http:
  - host: otlp-host
    port: 9999 # Defaults to 55681

# Export to Stackdriver via HTTP
# All config is optional, if running in GCP they will be obtained from the metadata endpoint
stackdriver-http:
  - project-id: some-project-id
    credentials-file: /path/to/credentials.json
    service-account-name: svcacc2 # Defaults to 'default'

# Export to Datadog agent - All config is optional
datadog:
  - host: agent-host # defaults to 'localhost'
    port: 9999 # defaults to 8126

# Export to NewRelic
new-relic:
  - api-key: 7c1989d1-e019-46bc-a04e-824fdf33b237
    endpoint: EU # defaults to US, may be a custom endpoint
```

#### Running

```bash
docker run -p7777:7777 -p7777:7777/udp -d --rm \
  --name trace4cats-collector-lite \
  -v /path/to/your/collector-config.yaml:/tmp/collector.yaml \
  janstenpickle/trace4cats-collector-lite:0.12.0-RC1 --config-file=/tmp/collector.yaml
```


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
[Kafka consumer]: https://kafka.apache.org/26/javadoc/?org/apache/kafka/clients/consumer/ConsumerConfig.html
[Kafka producer]: https://kafka.apache.org/26/javadoc/?org/apache/kafka/clients/producer/ProducerConfig.html
