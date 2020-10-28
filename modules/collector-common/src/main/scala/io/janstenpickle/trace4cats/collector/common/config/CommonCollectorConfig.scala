package io.janstenpickle.trace4cats.collector.common.config

import cats.data.NonEmptyList
import io.circe.Decoder
import io.jaegertracing.thrift.internal.senders.UdpSender
import io.janstenpickle.trace4cats.avro._
import io.janstenpickle.trace4cats.newrelic.Endpoint
import io.circe.generic.extras.semiauto._
import io.janstenpickle.trace4cats.avro.kafka.AvroKafkaConsumer.BatchConfig

case class CommonCollectorConfig(
  listener: ListenerConfig = ListenerConfig(),
  kafkaListener: Option[KafkaListenerConfig],
  forwarder: Option[ForwarderConfig],
  kafkaForwarder: Option[KafkaForwarderConfig],
  jaeger: Option[JaegerConfig],
  otlpHttp: Option[OtlpHttpConfig],
  stackdriverHttp: Option[StackdriverHttpConfig],
  datadog: Option[DatadogConfig],
  newRelic: Option[NewRelicConfig],
  sampling: Option[SamplingConfig],
  logSpans: Boolean = false,
  bufferSize: Int = 500
)
object CommonCollectorConfig {
  implicit val decoder: Decoder[CommonCollectorConfig] = deriveConfiguredDecoder
}

case class ListenerConfig(port: Int = DefaultPort)
object ListenerConfig {
  implicit val decoder: Decoder[ListenerConfig] = deriveConfiguredDecoder
}

case class KafkaListenerConfig(
  bootstrapServers: NonEmptyList[String],
  topic: String,
  group: String,
  batch: Option[BatchConfig] = None
)
object KafkaListenerConfig {
  implicit val batchDecoder: Decoder[BatchConfig] = deriveConfiguredDecoder
  implicit val decoder: Decoder[KafkaListenerConfig] = deriveConfiguredDecoder
}

case class ForwarderConfig(port: Int = DefaultPort, host: String)
object ForwarderConfig {
  implicit val decoder: Decoder[ForwarderConfig] = deriveConfiguredDecoder
}

case class KafkaForwarderConfig(bootstrapServers: NonEmptyList[String], topic: String)
object KafkaForwarderConfig {
  implicit val decoder: Decoder[KafkaForwarderConfig] = deriveConfiguredDecoder
}

case class JaegerConfig(port: Int = UdpSender.DEFAULT_AGENT_UDP_COMPACT_PORT, host: String)
object JaegerConfig {
  implicit val decoder: Decoder[JaegerConfig] = deriveConfiguredDecoder
}

case class OtlpHttpConfig(port: Int = 55681, host: String)
object OtlpHttpConfig {
  implicit val decoder: Decoder[OtlpHttpConfig] = deriveConfiguredDecoder
}

case class StackdriverHttpConfig(
  projectId: Option[String],
  credentialsFile: Option[String],
  serviceAccountName: Option[String]
)
object StackdriverHttpConfig {
  implicit val decoder: Decoder[StackdriverHttpConfig] = deriveConfiguredDecoder
}

case class DatadogConfig(port: Int = 8126, host: String = "localhost")
object DatadogConfig {
  implicit val decoder: Decoder[DatadogConfig] = deriveConfiguredDecoder
}

case class NewRelicConfig(apiKey: String, endpoint: Endpoint = Endpoint.US)
object NewRelicConfig {
  implicit val endpointDecoder: Decoder[Endpoint] = Decoder.decodeString.map {
    case "US" => Endpoint.US
    case "EU" => Endpoint.EU
    case endpoint => Endpoint.Observer(endpoint)
  }
  implicit val decoder: Decoder[NewRelicConfig] = deriveConfiguredDecoder
}

case class SamplingConfig(sampleProbability: Double, cacheTtlMinutes: Int = 2, maximumCacheSize: Long = 1_000_000)
object SamplingConfig {
  implicit val decoder: Decoder[SamplingConfig] = deriveConfiguredDecoder
}
