package io.janstenpickle.trace4cats.collector.common.config

import cats.data.{NonEmptyList, NonEmptyMap, NonEmptySet}
import cats.syntax.functor._
import io.circe.Decoder
import io.jaegertracing.thrift.internal.senders.UdpSender
import io.janstenpickle.trace4cats.avro._
import io.janstenpickle.trace4cats.newrelic.Endpoint
import io.circe.generic.extras.semiauto._
import io.janstenpickle.trace4cats.model.AttributeValue

case class CommonCollectorConfig(
  listener: ListenerConfig = ListenerConfig(),
  kafkaListener: Option[KafkaListenerConfig],
  batch: Option[BatchConfig],
  forwarder: Option[ForwarderConfig],
  kafkaForwarder: Option[KafkaForwarderConfig],
  jaeger: Option[JaegerConfig],
  otlpHttp: Option[OtlpHttpConfig],
  stackdriverHttp: Option[StackdriverHttpConfig],
  datadog: Option[DatadogConfig],
  newRelic: Option[NewRelicConfig],
  sampling: SamplingConfig = SamplingConfig(),
  attributeFiltering: FilteringConfig = FilteringConfig(),
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
  consumerConfig: Map[String, String] = Map.empty,
)
object KafkaListenerConfig {
  implicit val decoder: Decoder[KafkaListenerConfig] = deriveConfiguredDecoder
}

case class BatchConfig(size: Int, timeoutSeconds: Int)
object BatchConfig {
  implicit val decoder: Decoder[BatchConfig] = deriveConfiguredDecoder
}

case class ForwarderConfig(port: Int = DefaultPort, host: String)
object ForwarderConfig {
  implicit val decoder: Decoder[ForwarderConfig] = deriveConfiguredDecoder
}

case class KafkaForwarderConfig(
  bootstrapServers: NonEmptyList[String],
  topic: String,
  producerConfig: Map[String, String] = Map.empty
)
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

case class SamplingConfig(
  sampleProbability: Option[Double] = None,
  spanNames: Option[NonEmptySet[String]] = None,
  rate: Option[RateSamplingConfig] = None,
  cacheTtlMinutes: Int = 2,
  maxCacheSize: Long = 1000000,
  redis: Option[RedisStoreConfig] = None
)
object SamplingConfig {
  implicit val decoder: Decoder[SamplingConfig] = deriveConfiguredDecoder
}

case class RateSamplingConfig(maxBatchSize: Int, tokenRateMillis: Int)
object RateSamplingConfig {
  implicit val decoder: Decoder[RateSamplingConfig] = deriveConfiguredDecoder
}

sealed trait RedisStoreConfig
object RedisStoreConfig {
  case class RedisServer(host: String, port: Int = 6379) extends RedisStoreConfig
  object RedisServer {
    implicit val decoder: Decoder[RedisServer] = deriveConfiguredDecoder
  }
  case class RedisCluster(cluster: NonEmptyList[RedisServer]) extends RedisStoreConfig
  object RedisCluster {
    implicit val decoder: Decoder[RedisCluster] = deriveConfiguredDecoder
  }

  implicit val decoder: Decoder[RedisStoreConfig] = RedisServer.decoder.widen.or(RedisCluster.decoder.widen)
}

case class FilteringConfig(
  names: Option[NonEmptySet[String]] = None,
  values: Option[NonEmptySet[AttributeValue]] = None,
  nameValues: Option[NonEmptyMap[String, AttributeValue]] = None
)
object FilteringConfig {
  implicit val attributeValueDecoder: Decoder[AttributeValue] = List[Decoder[AttributeValue]](
    Decoder.decodeBoolean.map(AttributeValue.BooleanValue).widen,
    Decoder.decodeDouble.map(AttributeValue.DoubleValue).widen,
    Decoder.decodeLong.map(AttributeValue.LongValue).widen,
    Decoder.decodeString.map(AttributeValue.StringValue).widen,
    Decoder[NonEmptyList[Boolean]].map(AttributeValue.BooleanList).widen,
    Decoder[NonEmptyList[Double]].map(AttributeValue.DoubleList).widen,
    Decoder[NonEmptyList[Long]].map(AttributeValue.LongList).widen,
    Decoder[NonEmptyList[String]].map(AttributeValue.StringList).widen
  ).reduceLeft(_.or(_))

  implicit val decoder: Decoder[FilteringConfig] = deriveConfiguredDecoder
}
