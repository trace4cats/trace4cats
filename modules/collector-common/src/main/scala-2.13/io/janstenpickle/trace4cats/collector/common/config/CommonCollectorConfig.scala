package io.janstenpickle.trace4cats.collector.common.config

import cats.Eval
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
  kafkaListener: Option[KafkaListenerConfig] = None,
  batch: Option[BatchConfig] = None,
  tracing: Option[TracingConfig] = None,
  forwarders: List[ForwarderConfig] = List.empty,
  kafkaForwarders: List[KafkaForwarderConfig] = List.empty,
  jaeger: List[JaegerConfig] = List.empty,
  otlpHttp: List[OtlpHttpConfig] = List.empty,
  stackdriverHttp: List[StackdriverHttpConfig] = List.empty,
  datadog: List[DatadogConfig] = List.empty,
  newRelic: List[NewRelicConfig] = List.empty,
  zipkin: List[ZipkinConfig] = List.empty,
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

case class ZipkinConfig(port: Int = 9411, host: String = "localhost")
object ZipkinConfig {
  implicit val decoder: Decoder[ZipkinConfig] = deriveConfiguredDecoder
}

case class SamplingConfig(
  sampleProbability: Option[Double] = None,
  dropSpanNames: Option[NonEmptySet[String]] = None,
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
    Decoder.decodeBoolean.map(v => AttributeValue.BooleanValue(Eval.now(v))).widen,
    Decoder.decodeDouble.map(v => AttributeValue.DoubleValue(Eval.now(v))).widen,
    Decoder.decodeLong.map(v => AttributeValue.LongValue(Eval.now(v))).widen,
    Decoder.decodeString.map(v => AttributeValue.StringValue(Eval.now(v))).widen,
    Decoder[NonEmptyList[Boolean]].map(v => AttributeValue.BooleanList(Eval.now(v))).widen,
    Decoder[NonEmptyList[Double]].map(v => AttributeValue.DoubleList(Eval.now(v))).widen,
    Decoder[NonEmptyList[Long]].map(v => AttributeValue.LongList(Eval.now(v))).widen,
    Decoder[NonEmptyList[String]].map(v => AttributeValue.StringList(Eval.now(v))).widen
  ).reduceLeft(_.or(_))

  implicit val decoder: Decoder[FilteringConfig] = deriveConfiguredDecoder
}

case class TracingConfig(enabled: Boolean, sampleRate: Option[Double] = None)
object TracingConfig {
  implicit val decoder: Decoder[TracingConfig] = deriveConfiguredDecoder
}
